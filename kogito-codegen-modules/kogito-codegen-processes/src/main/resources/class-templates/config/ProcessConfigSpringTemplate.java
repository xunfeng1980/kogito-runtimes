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
package $Package$;

import java.util.List;

import org.kie.api.event.process.ProcessEventListener;
import org.kie.kogito.event.EventPublisher;
import org.kie.kogito.jobs.JobsService;
import org.kie.kogito.process.ProcessEventListenerConfig;
import org.kie.kogito.process.ProcessVersionResolver;
import org.kie.kogito.process.WorkItemHandlerConfig;
import org.kie.kogito.uow.UnitOfWorkManager;
import org.kie.kogito.uow.events.UnitOfWorkEventListener;

@org.springframework.stereotype.Component
public class ProcessConfig extends org.kie.kogito.process.impl.AbstractProcessConfig {

    @org.springframework.beans.factory.annotation.Autowired
    public ProcessConfig(
            List<WorkItemHandlerConfig> workItemHandlerConfig,
            List<UnitOfWorkManager> unitOfWorkManager,
            List<JobsService> jobsService,
            List<ProcessEventListenerConfig> processEventListenerConfigs,
            List<ProcessEventListener> processEventListeners,
            List<EventPublisher> eventPublishers,
            org.kie.kogito.conf.ConfigBean configBean,
            List<UnitOfWorkEventListener> unitOfWorkEventListeners,
            List<ProcessVersionResolver> versionResolver) {

        super(workItemHandlerConfig,
                processEventListenerConfigs,
                processEventListeners,
                unitOfWorkManager,
                jobsService,
                eventPublishers,
                configBean.getServiceUrl(),
                unitOfWorkEventListeners,
                versionResolver);
    }
}
