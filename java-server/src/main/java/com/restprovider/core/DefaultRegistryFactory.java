package com.restprovider.core;

import com.restprovider.controllers.AZPipelineController;
import com.restprovider.controllers.AzureController;
import com.restprovider.controllers.BrowserStackController;
import com.restprovider.controllers.BusinessController;
import com.restprovider.controllers.CosmosDBController;
import com.restprovider.controllers.DataFactoryController;
import com.restprovider.controllers.DatabricksController;
import com.restprovider.controllers.DataverseController;
import com.restprovider.controllers.FHIRController;
import com.restprovider.controllers.FileController;
import com.restprovider.controllers.JMeterController;
import com.restprovider.controllers.JenkinsController;
import com.restprovider.controllers.LogAnalyticsController;
import com.restprovider.controllers.MSDController;
import com.restprovider.controllers.MiscController;
import com.restprovider.controllers.OSController;
import com.restprovider.controllers.OfficeController;
import com.restprovider.controllers.OracleController;
import com.restprovider.controllers.PostmanController;
import com.restprovider.controllers.PowerBIController;
import com.restprovider.controllers.SQLServerController;
import com.restprovider.controllers.SequenceController;
import com.restprovider.controllers.SnowflakeController;
import com.restprovider.controllers.SonarQubeController;
import com.restprovider.controllers.StorageAccountController;
import com.restprovider.controllers.StringController;
import com.restprovider.controllers.SynapseController;
import com.restprovider.controllers.WaitController;

public final class DefaultRegistryFactory {
    private DefaultRegistryFactory() {
    }

    public static ControllerRegistry createDefaultRegistry() {
        ControllerRegistry registry = new ControllerRegistry();
        registry.register(new AZPipelineController());
        registry.register(new AzureController());
        registry.register(new BrowserStackController());
        registry.register(new BusinessController());
        registry.register(new CosmosDBController());
        registry.register(new DatabricksController());
        registry.register(new DataFactoryController());
        registry.register(new DataverseController());
        registry.register(new FHIRController());
        registry.register(new FileController());
        registry.register(new JenkinsController());
        registry.register(new JMeterController());
        registry.register(new LogAnalyticsController());
        registry.register(new MiscController());
        registry.register(new MSDController());
        registry.register(new OfficeController());
        registry.register(new OracleController());
        registry.register(new OSController());
        registry.register(new PostmanController());
        registry.register(new PowerBIController());
        registry.register(new SequenceController());
        registry.register(new SnowflakeController());
        registry.register(new SonarQubeController());
        registry.register(new SQLServerController());
        registry.register(new StorageAccountController());
        registry.register(new StringController());
        registry.register(new SynapseController());
        registry.register(new WaitController());
        return registry;
    }
}
