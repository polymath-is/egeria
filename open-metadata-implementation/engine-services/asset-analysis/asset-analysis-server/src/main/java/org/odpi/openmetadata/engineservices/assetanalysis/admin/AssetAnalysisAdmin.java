/* SPDX-License-Identifier: Apache 2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.openmetadata.engineservices.assetanalysis.admin;

import org.odpi.openmetadata.accessservices.discoveryengine.client.DiscoveryConfigurationClient;
import org.odpi.openmetadata.accessservices.discoveryengine.client.DiscoveryEngineClient;
import org.odpi.openmetadata.accessservices.discoveryengine.client.ODFRESTClient;
import org.odpi.openmetadata.adminservices.configuration.properties.EngineConfig;
import org.odpi.openmetadata.adminservices.configuration.properties.EngineServiceConfig;
import org.odpi.openmetadata.adminservices.configuration.registration.EngineServiceAdmin;
import org.odpi.openmetadata.adminservices.configuration.registration.EngineServiceDescription;
import org.odpi.openmetadata.adminservices.ffdc.exception.OMAGConfigurationErrorException;
import org.odpi.openmetadata.engineservices.assetanalysis.ffdc.AssetAnalysisAuditCode;
import org.odpi.openmetadata.engineservices.assetanalysis.ffdc.AssetAnalysisErrorCode;
import org.odpi.openmetadata.engineservices.assetanalysis.handlers.DiscoveryConfigurationRefreshHandler;
import org.odpi.openmetadata.engineservices.assetanalysis.handlers.DiscoveryEngineHandler;
import org.odpi.openmetadata.engineservices.assetanalysis.server.AssetAnalysisInstance;
import org.odpi.openmetadata.frameworks.auditlog.AuditLog;
import org.odpi.openmetadata.frameworks.connectors.ffdc.InvalidParameterException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AssetAnalysisAdmin extends EngineServiceAdmin
{
    private AssetAnalysisInstance assetAnalysisInstance = null;

    /**
     * Initialize engine service.
     *
     * @param localServerId unique identifier of this server
     * @param localServerName name of this server
     * @param auditLog link to the repository responsible for servicing the REST calls.
     * @param localServerUserId user id for this server to use if sending REST requests and processing inbound messages.
     * @param localServerPassword password for this server to use if sending REST requests.
     * @param maxPageSize maximum number of records that can be requested on the pageSize parameter
     * @param engineServiceConfig details of the options and the engines to run.
     * @throws OMAGConfigurationErrorException an issue in the configuration prevented initialization
     */
    public void initialize(String              localServerId,
                           String              localServerName,
                           AuditLog            auditLog,
                           String              localServerUserId,
                           String              localServerPassword,
                           int                 maxPageSize,
                           EngineServiceConfig engineServiceConfig) throws OMAGConfigurationErrorException
    {
        final String actionDescription = "initialize";
        final String methodName        = "initialize";

        ODFRESTClient restClient;

        this.auditLog = auditLog;
        this.localServerName = localServerName;

        auditLog.logMessage(actionDescription, AssetAnalysisAuditCode.SERVER_INITIALIZING.getMessageDefinition(localServerName));

        try
        {
            this.validateConfigDocument(engineServiceConfig, methodName);

            /*
             * The configuration for the discovery engines is located in an open metadata server.
             * It is accessed through the Discovery Engine OMAS.  If the values needed to call
             * the Discovery Engine OMAS are not present in the configuration then there is no point in continuing
             * and an exception is thrown.
             */
            String             accessServiceRootURL    = this.getAccessServiceRootURL(engineServiceConfig);
            String             accessServiceServerName = this.getAccessServiceServerName(engineServiceConfig);
            List<EngineConfig> discoveryEngines        = this.getEngines(engineServiceConfig);

            /*
             * Create the client for accessing the configuration.  The Discovery Engine OMAS has a specific client for retrieving
             * configuration. Any problems results in an exception.
             */
            DiscoveryConfigurationClient configurationClient;
            try
            {
                if ((localServerName != null) && (localServerPassword != null))
                {
                    restClient = new ODFRESTClient(accessServiceServerName,
                                                   accessServiceRootURL,
                                                   localServerUserId,
                                                   localServerPassword);
                }
                else
                {
                    restClient = new ODFRESTClient(accessServiceServerName, accessServiceRootURL);
                }

                configurationClient = new DiscoveryConfigurationClient(accessServiceServerName,
                                                                       accessServiceRootURL,
                                                                       restClient,
                                                                       maxPageSize,
                                                                       auditLog,
                                                                       localServerId);
            }
            catch (InvalidParameterException error)
            {
                throw new OMAGConfigurationErrorException(error.getReportedErrorMessage(), error);
            }

            /*
             * Create a discovery handler for each of the discovery engines.
             */
            Map<String, DiscoveryEngineHandler> discoveryEngineHandlers = this.getDiscoveryEngineHandlers(discoveryEngines,
                                                                                                          accessServiceRootURL,
                                                                                                          accessServiceServerName,
                                                                                                          localServerUserId,
                                                                                                          configurationClient,
                                                                                                          restClient,
                                                                                                          maxPageSize);

            if (discoveryEngineHandlers == null)
            {
                auditLog.logMessage(actionDescription, AssetAnalysisAuditCode.NO_DISCOVERY_ENGINES_STARTED.getMessageDefinition(localServerName));

                throw new OMAGConfigurationErrorException(AssetAnalysisErrorCode.NO_DISCOVERY_ENGINES_STARTED.getMessageDefinition(localServerName),
                                                          this.getClass().getName(),
                                                          methodName);
            }

            /*
             * Register a listener for the Discovery Engine OMAS out topic.  This call will fail if
             * the metadata server is not running so a separate thread is created to retry the registration request at
             * intervals to wait for the metadata server to restart.  It will also try to retrieve the configuration
             * for the discovery engines.
             */
            DiscoveryConfigurationRefreshHandler configurationHandler = new DiscoveryConfigurationRefreshHandler(discoveryEngineHandlers,
                                                                                                                 configurationClient,
                                                                                                                 auditLog,
                                                                                                                 localServerUserId,
                                                                                                                 localServerName,
                                                                                                                 accessServiceServerName,
                                                                                                                 accessServiceRootURL);
            Thread thread = new Thread(configurationHandler, configurationHandler.getClass().getName());
            thread.start();

            /*
             * Set up the REST APIs.
             */
            assetAnalysisInstance = new AssetAnalysisInstance(localServerName,
                                                              EngineServiceDescription.ASSET_ANALYSIS_OMES.getEngineServiceName(),
                                                              auditLog,
                                                              localServerUserId,
                                                              maxPageSize,
                                                              engineServiceConfig.getOMAGServerPlatformRootURL(),
                                                              engineServiceConfig.getOMAGServerName(),
                                                              discoveryEngineHandlers);
        }
        catch (Throwable error)
        {
            auditLog.logException(actionDescription,
                                  AssetAnalysisAuditCode.SERVICE_INSTANCE_FAILURE.getMessageDefinition(localServerName, error.getMessage()),
                                  error.toString(),
                                  error);

            throw new OMAGConfigurationErrorException(AssetAnalysisErrorCode.SERVICE_INSTANCE_FAILURE.getMessageDefinition(localServerName, error.getMessage()),
                                                      this.getClass().getName(),
                                                      methodName,
                                                      error);
        }
    }


    /**
     * Create the list of discovery engine handlers.
     *
     * @param discoveryEngines list of discovery engines
     * @param accessServiceRootURL URL Root for the Discovery Engine OMAS
     * @param accessServiceServerName Server Name for the Discovery Engine OMAS
     * @param localServerUserId user id for this server to use if sending REST requests and processing inbound messages.
     * @param configurationClient client to retrieve configuration from
     * @param odfRESTClient client for calling REST APIs
     * @param maxPageSize maximum number of records that can be requested on the pageSize parameter
     * @return map of discovery engine GUIDs to handlers
     * @throws OMAGConfigurationErrorException problem with config
     */
    private Map<String, DiscoveryEngineHandler>  getDiscoveryEngineHandlers(List<EngineConfig>           discoveryEngines,
                                                                            String                       accessServiceRootURL,
                                                                            String                       accessServiceServerName,
                                                                            String                       localServerUserId,
                                                                            DiscoveryConfigurationClient configurationClient,
                                                                            ODFRESTClient                odfRESTClient,
                                                                            int                          maxPageSize) throws OMAGConfigurationErrorException
    {
        final String methodName        = "getDiscoveryEngineHandlers";

        Map<String, DiscoveryEngineHandler> discoveryEngineHandlers = new HashMap<>();

        for (EngineConfig   discoveryEngine : discoveryEngines)
        {
            if (discoveryEngine != null)
            {
                DiscoveryEngineClient discoveryEngineClient;
                try
                {
                    discoveryEngineClient = new DiscoveryEngineClient(accessServiceServerName,
                                                                          accessServiceRootURL,
                                                                          odfRESTClient,
                                                                          auditLog);

                }
                catch (Throwable  error)
                {
                    /*
                     * Unable to create a client to the Discovery Engine.  This is a config problem that is not possible to
                     * work around so shut down the server.
                     */
                    throw new OMAGConfigurationErrorException(AssetAnalysisErrorCode.NO_DISCOVERY_ENGINE_CLIENT.getMessageDefinition(localServerName,
                                                                                                                                     discoveryEngine.getEngineQualifiedName(),
                                                                                                                                     error.getClass().getName(),
                                                                                                                                     error.getMessage()),
                                                              this.getClass().getName(),
                                                              methodName);
                }

                /*
                 * Create a handler for the discovery engine.
                 */
                DiscoveryEngineHandler  handler = new DiscoveryEngineHandler(discoveryEngine,
                                                                             accessServiceServerName,
                                                                             localServerUserId,
                                                                             configurationClient,
                                                                             discoveryEngineClient,
                                                                             auditLog,
                                                                             maxPageSize);

                discoveryEngineHandlers.put(discoveryEngine.getEngineQualifiedName(), handler);
            }
        }

        if (discoveryEngineHandlers.isEmpty())
        {
            return null;
        }
        else
        {
            return discoveryEngineHandlers;
        }
    }


    /**
     * Shutdown the view service.
     */
    public void shutdown()
    {
        final String actionDescription = "shutdown";

        auditLog.logMessage(actionDescription, AssetAnalysisAuditCode.SERVER_SHUTTING_DOWN.getMessageDefinition(localServerName));

        assetAnalysisInstance.shutdown();

        auditLog.logMessage(actionDescription, AssetAnalysisAuditCode.SERVER_SHUTDOWN.getMessageDefinition(localServerName));

    }
}
