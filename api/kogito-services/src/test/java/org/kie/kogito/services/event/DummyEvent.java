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
package org.kie.kogito.services.event;

import java.util.Objects;

public class DummyEvent {

    private String dummyField;

    @SuppressWarnings("unused")
    public DummyEvent() {
    }

    public DummyEvent(String dummyField) {
        this.dummyField = dummyField;
    }

    @SuppressWarnings("unused")
    public String getDummyField() {
        return dummyField;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DummyEvent)) {
            return false;
        }
        DummyEvent that = (DummyEvent) o;
        return Objects.equals(getDummyField(), that.getDummyField());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getDummyField());
    }
}
