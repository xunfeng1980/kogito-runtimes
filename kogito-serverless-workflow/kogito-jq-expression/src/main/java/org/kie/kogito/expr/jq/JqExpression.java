/*
 * Copyright 2021 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kie.kogito.expr.jq;

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Supplier;

import org.kie.kogito.internal.process.runtime.KogitoProcessContext;
import org.kie.kogito.jackson.utils.JsonObjectUtils;
import org.kie.kogito.jackson.utils.ObjectMapperFactory;
import org.kie.kogito.process.expr.Expression;
import org.kie.kogito.serverless.workflow.utils.ExpressionHandlerUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import net.thisptr.jackson.jq.JsonQuery;
import net.thisptr.jackson.jq.Output;
import net.thisptr.jackson.jq.Scope;
import net.thisptr.jackson.jq.Versions;
import net.thisptr.jackson.jq.exception.JsonQueryException;

public class JqExpression implements Expression {

    private final Supplier<Scope> scope;
    private final String expr;
    private JsonQuery query;

    public JqExpression(Supplier<Scope> scope, String expr) {
        this.expr = expr;
        this.scope = scope;
    }

    private interface TypedOutput extends Output {
        Object getResult();
    }

    private TypedOutput output(Class<?> returnClass) {
        TypedOutput out;
        if (String.class.isAssignableFrom(returnClass)) {
            out = new StringOutput();
        } else if (Collection.class.isAssignableFrom(returnClass)) {
            out = new CollectionOutput();
        } else {
            out = new JsonNodeOutput();
        }
        return out;
    }

    private static class StringOutput implements TypedOutput {
        StringBuilder sb = new StringBuilder();

        @Override
        public void emit(JsonNode out) throws JsonQueryException {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(out.asText());
        }

        @Override
        public Object getResult() {
            return sb.toString();
        }

    }

    private static class CollectionOutput implements TypedOutput {
        Collection<Object> result = new ArrayList<>();

        @Override
        public void emit(JsonNode out) throws JsonQueryException {
            Object obj = JsonObjectUtils.toJavaValue(out);
            if (obj instanceof Collection)
                result.addAll((Collection) obj);
            else {
                result.add(obj);
            }
        }

        @Override
        public Object getResult() {
            return result;
        }

    }

    private static class JsonNodeOutput implements TypedOutput {

        private JsonNode result;
        private boolean arrayCreated;

        @Override
        public void emit(JsonNode out) throws JsonQueryException {
            if (this.result == null) {
                this.result = out;
            } else if (!arrayCreated) {
                ArrayNode newNode = ObjectMapperFactory.get().createArrayNode();
                newNode.add(this.result).add(out);
                this.result = newNode;
                arrayCreated = true;
            } else {
                ((ArrayNode) this.result).add(out);
            }
        }

        @Override
        public JsonNode getResult() {
            return result;
        }
    }

    @Override
    public <T> T eval(Object target, Class<T> returnClass, KogitoProcessContext context) {
        return eval(JsonObjectUtils.fromValue(target), returnClass, context);
    }

    @Override
    public void assign(Object target, Object value, KogitoProcessContext context) {
        JsonNode targetNode = JsonObjectUtils.fromValue(target);
        ExpressionHandlerUtils.assign(targetNode, eval(targetNode, JsonNode.class, context), JsonObjectUtils.fromValue(value), expr);
    }

    private Scope getScope(KogitoProcessContext processInfo) {
        Scope childScope = Scope.newChildScope(scope.get());
        childScope.setValue(ExpressionHandlerUtils.SECRET_MAGIC, new FunctionJsonNode(ExpressionHandlerUtils::getSecret));
        childScope.setValue(ExpressionHandlerUtils.CONTEXT_MAGIC, new FunctionJsonNode(ExpressionHandlerUtils.getContextFunction(processInfo)));
        childScope.setValue(ExpressionHandlerUtils.CONST_MAGIC, ExpressionHandlerUtils.getConstants(processInfo));
        return childScope;
    }

    private <T> T eval(JsonNode context, Class<T> returnClass, KogitoProcessContext processInfo) {
        try {
            TypedOutput output = output(returnClass);
            compile();
            query.apply(getScope(processInfo), context, output);
            return JsonObjectUtils.convertValue(output.getResult(), returnClass);
        } catch (JsonQueryException e) {
            throw new IllegalArgumentException("Unable to evaluate content " + context + " using expr " + expr, e);
        }
    }

    private void compile() throws JsonQueryException {
        if (this.query == null) {
            this.query = JsonQuery.compile(expr, Versions.JQ_1_6);
        }
    }

    @Override
    public boolean isValid() {
        try {
            compile();
            return true;
        } catch (JsonQueryException e) {
            return false;
        }
    }

    @Override
    public String asString() {
        return expr;
    }
}
