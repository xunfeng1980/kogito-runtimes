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

package org.kie.kogito.quarkus.processes.deployment;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.eclipse.microprofile.config.ConfigProvider;
import org.kie.kogito.quarkus.common.deployment.KogitoDataIndexServiceAvailableBuildItem;
import org.kie.kogito.quarkus.processes.devservices.DataIndexEventPublisher;
import org.kie.kogito.quarkus.processes.devservices.DataIndexInMemoryContainer;
import org.kie.kogito.quarkus.processes.devservices.DevModeWorkflowLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.processor.DotNames;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.IsDockerWorking;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CuratedApplicationShutdownBuildItem;
import io.quarkus.deployment.builditem.DevServicesSharedNetworkBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.builditem.SystemPropertyBuildItem;
import io.quarkus.deployment.console.ConsoleInstalledBuildItem;
import io.quarkus.deployment.console.StartupLogCompressor;
import io.quarkus.deployment.dev.devservices.GlobalDevServicesConfig;
import io.quarkus.deployment.logging.LoggingSetupBuildItem;
import io.quarkus.devservices.common.ContainerAddress;
import io.quarkus.devservices.common.ContainerLocator;
import io.quarkus.runtime.LaunchMode;

import static org.kie.kogito.quarkus.processes.devservices.DataIndexEventPublisher.KOGITO_DATA_INDEX;
import static org.kie.kogito.quarkus.processes.devservices.DataIndexInMemoryContainer.LATEST;

/**
 * Starts a Data Index as dev service if needed.
 */
public class KogitoDevServicesProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(KogitoDevServicesProcessor.class);
    private static final ContainerLocator LOCATOR = new ContainerLocator(DataIndexInMemoryContainer.DEV_SERVICE_LABEL, DataIndexInMemoryContainer.PORT);
    private static final Map<String, Properties> DEVSERVICES_PROPS = new ConcurrentHashMap<>();

    static volatile Closeable closeable;
    static volatile DataIndexDevServiceConfig cfg;
    static volatile boolean first = true;

    private final IsDockerWorking isDockerWorking = new IsDockerWorking(true);

    @BuildStep
    public void logger(BuildProducer<AdditionalBeanBuildItem> additionalBean, LaunchModeBuildItem launchMode, KogitoBuildTimeConfig config, Capabilities capabilities) {
        if (shouldInclude(launchMode, config, capabilities)) {
            additionalBean.produce(AdditionalBeanBuildItem.builder().addBeanClass(DevModeWorkflowLogger.class).setUnremovable().setDefaultScope(DotNames.APPLICATION_SCOPED).build());
        }
    }

    private static boolean shouldInclude(LaunchModeBuildItem launchMode, KogitoBuildTimeConfig config, Capabilities capabilities) {
        return capabilities.isMissing("org.kie.kogito.serverless-workflow") && (launchMode.getLaunchMode().isDevOrTest() || config.alwaysInclude);
    }

    private static String getDataIndexImageVersion() {
        Package aPackage = KogitoDevServicesProcessor.class.getPackage();
        String version = null;
        if (aPackage != null) {
            version = aPackage.getImplementationVersion();
            if (version == null) {
                version = aPackage.getSpecificationVersion();
            }
        }

        if (version == null) {
            return LATEST;
        }

        ArtifactVersion av = new DefaultArtifactVersion(version);
        if ("SNAPSHOT".equals(av.getQualifier())) {
            return LATEST;
        } else {
            return av.getMajorVersion() + "." + av.getMinorVersion();
        }
    }

    @BuildStep(onlyIf = { GlobalDevServicesConfig.Enabled.class, IsDevelopment.class })
    public void startDataIndexDevService(
            BuildProducer<AdditionalBeanBuildItem> additionalBean,
            BuildProducer<SystemPropertyBuildItem> systemProperties,
            BuildProducer<KogitoDataIndexServiceAvailableBuildItem> dataIndexServiceAvailableBuildItemBuildProducer,
            LaunchModeBuildItem launchMode,
            KogitoBuildTimeConfig buildTimeConfig,
            List<DevServicesSharedNetworkBuildItem> devServicesSharedNetwork,
            Optional<ConsoleInstalledBuildItem> consoleInstalled,
            CuratedApplicationShutdownBuildItem applicationShutdown,
            LoggingSetupBuildItem loggingSetup) {

        DataIndexDevServiceConfig configuration = getConfiguration(buildTimeConfig);

        if (configuration.devServicesEnabled && isDockerWorking.getAsBoolean()) {
            additionalBean.produce(AdditionalBeanBuildItem.builder().addBeanClass(DataIndexEventPublisher.class).build());
            Integer port = ConfigProvider.getConfig().getOptionalValue("quarkus.http.port", Integer.class).orElse(8080);
            Testcontainers.exposeHostPorts(port);
            systemProperties.produce(new SystemPropertyBuildItem("kogito.service.url", "http://host.testcontainers.internal:" + port));
        }

        LOGGER.info("Dev Services for Kogito Data Index using image {}", configuration.imageName);
        if (closeable != null) {
            boolean shouldShutdown = !configuration.equals(cfg);
            if (!shouldShutdown) {
                // Signal the service is available when DevServices may have restarted but the service not
                dataIndexServiceAvailableBuildItemBuildProducer.produce(new KogitoDataIndexServiceAvailableBuildItem());
                return;
            }
            shutdownDataIndex();
            cfg = null;
        }

        StartupLogCompressor compressor = new StartupLogCompressor(
                (launchMode.isTest() ? "(test) " : "") + "Kogito Data Index Dev Service starting:",
                consoleInstalled, loggingSetup);

        DataIndexInstance dataIndex;
        try {
            dataIndex = startDataIndex(configuration, launchMode, !devServicesSharedNetwork.isEmpty());
            if (dataIndex != null) {
                // Signal the service is available
                dataIndexServiceAvailableBuildItemBuildProducer.produce(new KogitoDataIndexServiceAvailableBuildItem());
                closeable = dataIndex.getCloseable();
                systemProperties.produce(new SystemPropertyBuildItem(KOGITO_DATA_INDEX, dataIndex.getUrl()));
            }
            compressor.close();
        } catch (Throwable t) {
            compressor.closeAndDumpCaptured();
            throw new RuntimeException("Failed to start Kogito Data Index Dev Services", t);
        }

        // Configure the watch dog
        if (first) {
            first = false;
            Runnable closeTask = () -> {
                if (closeable != null) {
                    shutdownDataIndex();
                }
                first = true;
                closeable = null;
                cfg = null;
            };
            applicationShutdown.addCloseTask(closeTask, true);
        }
        cfg = configuration;

        if (dataIndex != null && dataIndex.isOwner()) {
            LOGGER.info(
                    "Dev Services for Kogito Data Index started at {}",
                    dataIndex.getUrl());
        }

    }

    private void shutdownDataIndex() {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Throwable e) {
                LOGGER.error("Failed to stop Kogito Data Index", e);
            } finally {
                closeable = null;
            }
        }
    }

    private DataIndexInstance startDataIndex(DataIndexDevServiceConfig config, LaunchModeBuildItem launchMode, boolean useSharedNetwork) {
        if (!config.devServicesEnabled) {
            // explicitly disabled
            LOGGER.debug("Not starting dev services for Kogito, as it has been disabled in the config.");
            return null;
        }

        if (!isDockerWorking.getAsBoolean()) {
            LOGGER.warn(
                    "Docker isn't working, unable to start Data Index image.");
            return null;
        }

        final Optional<ContainerAddress> maybeContainerAddress = LOCATOR.locateContainer(config.serviceName,
                config.shared,
                launchMode.getLaunchMode());

        // Starting Data Index
        final Supplier<DataIndexInstance> dataIndexSupplier = () -> {
            try {

                DataIndexInMemoryContainer container = new DataIndexInMemoryContainer(
                        DockerImageName.parse(config.imageName),
                        config.fixedExposedPort,
                        launchMode.getLaunchMode() == LaunchMode.DEVELOPMENT ? config.serviceName : null,
                        useSharedNetwork);
                container.start();

                return new DataIndexInstance(container.getUrl(), container::close);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        };

        return maybeContainerAddress.map(containerAddress -> new DataIndexInstance(containerAddress.getUrl(), null))
                .orElseGet(dataIndexSupplier);
    }

    private DataIndexDevServiceConfig getConfiguration(KogitoBuildTimeConfig cfg) {
        KogitoDevServicesBuildTimeConfig devServicesConfig = cfg.devservices;
        return new DataIndexDevServiceConfig(devServicesConfig);
    }

    private static class DataIndexInstance {
        private final String url;
        private final Closeable closeable;

        public DataIndexInstance(String url, Closeable closeable) {
            this.url = url;
            this.closeable = closeable;
        }

        public boolean isOwner() {
            return closeable != null;
        }

        public String getUrl() {
            return url;
        }

        public Closeable getCloseable() {
            return closeable;
        }
    }

    private static final class DataIndexDevServiceConfig {

        private final boolean devServicesEnabled;
        private final String imageName;
        private final Integer fixedExposedPort;
        private final boolean shared;
        private final String serviceName;

        public DataIndexDevServiceConfig(KogitoDevServicesBuildTimeConfig config) {
            this.devServicesEnabled = config.enabled.orElse(true);
            //TODO Revert to ConfigureUtil.getDefaultImageNameFor
            this.imageName = config.imageName.orElseGet(() -> getDefaultImageNameFor("data-index") + ":" + getDataIndexImageVersion());
            this.fixedExposedPort = config.port.orElse(0);
            this.shared = config.shared;
            this.serviceName = config.serviceName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            DataIndexDevServiceConfig that = (DataIndexDevServiceConfig) o;
            return devServicesEnabled == that.devServicesEnabled && Objects.equals(imageName, that.imageName)
                    && Objects.equals(fixedExposedPort, that.fixedExposedPort);
        }

        @Override
        public int hashCode() {
            return Objects.hash(devServicesEnabled, imageName, fixedExposedPort);
        }
    }

    //TODO Temporary copying over contents of Quarkus ConfigureUtil.getDefaultImageNameFor
    //This should be replaced once Quarkus LTS moves to 2.8+ where this API is available
    private static String getDefaultImageNameFor(String devserviceName) {
        var imageName = DEVSERVICES_PROPS.computeIfAbsent(devserviceName, KogitoDevServicesProcessor::loadProperties)
                .getProperty("default.image");
        if (imageName == null) {
            throw new IllegalArgumentException("No default.image configured for " + devserviceName);
        }
        return imageName;
    }

    private static Properties loadProperties(String devserviceName) {
        var fileName = devserviceName + "-devservice.properties";
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(fileName)) {
            if (in == null) {
                throw new IllegalArgumentException(fileName + " not found on classpath");
            }
            var properties = new Properties();
            properties.load(in);
            return properties;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
