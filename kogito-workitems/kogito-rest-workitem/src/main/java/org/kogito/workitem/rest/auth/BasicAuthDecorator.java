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
package org.kogito.workitem.rest.auth;

import java.util.Map;

import org.kie.kogito.internal.process.runtime.KogitoWorkItem;

import io.vertx.mutiny.ext.web.client.HttpRequest;

import static org.kie.kogito.internal.utils.ConversionUtils.isEmpty;
import static org.kogito.workitem.rest.RestWorkItemHandler.PASSWORD;
import static org.kogito.workitem.rest.RestWorkItemHandler.USER;
import static org.kogito.workitem.rest.RestWorkItemHandlerUtils.getParam;

public class BasicAuthDecorator implements AuthDecorator {

    @Override
    public void decorate(KogitoWorkItem item, Map<String, Object> parameters, HttpRequest<?> request) {
        String user = getParam(parameters, USER, String.class, null);
        String password = getParam(parameters, PASSWORD, String.class, null);

        if (!isEmpty(user) && !isEmpty(password)) {
            request.basicAuthentication(user, password);
        }
    }
}
