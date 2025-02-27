/*
 * Copyright 2022 Red Hat, Inc. and/or its affiliates.
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
package org.kie.kogito.serverless.workflow.operationid;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.Optional;

import org.kie.kogito.jackson.utils.ObjectMapperFactory;
import org.kie.kogito.serverless.workflow.parser.ParserContext;
import org.kie.kogito.serverless.workflow.parser.handlers.ActionResource;
import org.kie.kogito.serverless.workflow.parser.handlers.ActionType;
import org.kie.kogito.serverless.workflow.utils.ServerlessWorkflowUtils;
import org.kie.kogito.serverless.workflow.utils.URIDefinitions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;

import io.serverlessworkflow.api.Workflow;
import io.serverlessworkflow.api.functions.FunctionDefinition;

import static org.kie.kogito.serverless.workflow.utils.ServerlessWorkflowUtils.onlyChars;
import static org.kie.kogito.serverless.workflow.utils.ServerlessWorkflowUtils.removeExt;

public abstract class AbstractWorkflowOperationIdFactory implements WorkflowOperationIdFactory {
    @Override
    public WorkflowOperationId from(Workflow workflow, FunctionDefinition function, Optional<ParserContext> context) {
        ActionType actionType = ActionType.from(function);
        ActionResource actionResource = actionType.getActionResource(function);
        Optional<String> convertedUri = convertURI(workflow, context, actionResource.getUri());
        final URI uri;
        final String fileName;
        if (convertedUri.isPresent()) {
            uri = URI.create(convertedUri.get());
            fileName = actionResource.getUri();
        } else {
            uri = URI.create(actionResource.getUri());
            fileName = getFileName(workflow, function, context, uri, actionResource.getOperation(), actionResource.getService());
        }
        return new WorkflowOperationId(uri, actionResource.getOperation(), actionResource.getService(), fileName, onlyChars(removeExt(fileName.toLowerCase())));
    }

    private Optional<String> convertURI(Workflow workflow, Optional<ParserContext> context, String uri) {
        return workflow.getExtensions().stream().filter(e -> e.getExtensionId().equals(URIDefinitions.URI_DEFINITIONS)).findFirst().map(URIDefinitions.class::cast)
                .map(def -> getUriDefinitions(workflow, context, def)).filter(node -> node.has(uri)).map(node -> node.get(uri).asText());
    }

    private JsonNode getUriDefinitions(Workflow workflow, Optional<ParserContext> context, URIDefinitions uriDefinitions) {
        JsonNode definitions = uriDefinitions.getDefinitions();
        if (definitions == null || definitions.isNull()) {
            String uri = uriDefinitions.getURI();
            definitions = uri == null ? NullNode.instance : ServerlessWorkflowUtils.loadResourceFile(uri, Optional.of(workflow), context, null).map(bytes -> {
                try {
                    return ObjectMapperFactory.get().readTree(bytes);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }).orElse(NullNode.instance);
            uriDefinitions.setDefinitions(definitions);
        }
        return definitions;
    }

    protected abstract String getFileName(Workflow workflow, FunctionDefinition function, Optional<ParserContext> context, URI uri, String operation, String service);
}
