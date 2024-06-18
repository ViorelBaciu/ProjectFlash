package net.xqhs.flash.core;

import net.xqhs.flash.core.node.Node;
import net.xqhs.flash.core.util.ClassFactory;
import net.xqhs.flash.core.util.MultiTreeMap;
import net.xqhs.flash.core.util.PlatformUtils;
import net.xqhs.util.logging.UnitComponentExt;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.*;

public class DynamicNode extends Node {
    private final List<String> agents = new ArrayList<>();

    public DynamicNode(MultiTreeMap nodeConfiguration) throws RemoteException {
        super(nodeConfiguration);
    }

    public void dynamicAddAgent(String[] cliArgs) {
        System.out.println("Dynamically adding agent with args: " + Arrays.toString(cliArgs));
        List<String> cliArgsList = new LinkedList<>(Arrays.asList(cliArgs));
        try {
            validateArguments(cliArgsList);
            addEntitiesFromCLIArguments(cliArgsList);
            agents.add(cliArgsList.toString());
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
        }
    }

    public void dynamicAddShard(String[] cliArgs) {
        System.out.println("Dynamically adding shard with args: " + Arrays.toString(cliArgs));
        List<String> cliArgsList = new LinkedList<>(Arrays.asList(cliArgs));
        try {
            validateArguments(cliArgsList);
            addEntitiesFromCLIArguments(cliArgsList);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
        }
    }

    private void validateArguments(List<String> cliArgs) {
        for (String arg : cliArgs) {
            if (!arg.contains(":")) {
                throw new IllegalArgumentException("Invalid argument format: " + arg + ". Expected format is -<arg_name>:<arg_value>");
            }

            if(!arg.startsWith("-")) {
                throw new IllegalArgumentException("Invalid argument format: " + arg + ". Expected format is -<arg_name>:<arg_value>");
            }
        }
    }

    public void addEntitiesFromCLIArguments(List<String> cliArgs) {
        try {
            MultiTreeMap rootTree = this.getDeploymentConfiguration();
            DeploymentConfiguration deploymentConfiguration = new DeploymentConfiguration();
            List<String> autoCreated = new LinkedList<>();
            Map<String, String> name_ids = new HashMap<>();
            UnitComponentExt log = new UnitComponentExt("Dynamic Loader"); // Logger instance

            // Use DeploymentProxy to create a valid context
            DeploymentConfigurationProxy.PublicCtxtTriple baseContext = new DeploymentConfigurationProxy.PublicCtxtTriple(CategoryName.DEPLOYMENT.s(), rootTree, rootTree.getSingleTree(null));

            List<String> argsList = new ArrayList<>();
            cliArgs.forEach(arg -> argsList.add(arg.replace('<', ' ').replace('>', ' ').trim()));
            Iterator<String> argsIterator = argsList.iterator();

            // it parsing the CLI args into the configuration tree using DeploymentProxy
            DeploymentConfigurationProxy.publicReadCLIArgs(deploymentConfiguration, argsIterator, baseContext, rootTree, autoCreated, name_ids, log);

            // Load entities parsed from CLI
            List<MultiTreeMap> updatedEntities = deploymentConfiguration.getEntityList();
            List<MultiTreeMap> filteredEntities = DeploymentConfiguration.filterCategoryInContext(updatedEntities, null, null);
            loadParsedEntities(filteredEntities, updatedEntities, deploymentConfiguration, autoCreated);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadParsedEntities(List<MultiTreeMap> newEntitiesList, List<MultiTreeMap> subordinateEntities, DeploymentConfiguration deploymentConfigObj, List<String> autoCreated) {
        try {
            ClassFactory classFactory = PlatformUtils.getClassFactory();
            Loader<?> defaultLoader = new SimpleLoader();
            String deploymentID = subordinateEntities.get(0).getSingleValue(DeploymentConfiguration.LOCAL_ID_ATTRIBUTE); // Adjust based on the available entities

            for (MultiTreeMap entityConfig : newEntitiesList) {
                String entityTypeName = entityConfig.get(DeploymentConfiguration.NAME_ATTRIBUTE_NAME);
                Entity<?> entity = defaultLoader.load(entityConfig, null, subordinateEntities);

                if (entity != null) {
                    String local_id = entityConfig.getSingleValue(DeploymentConfiguration.LOCAL_ID_ATTRIBUTE);
                    this.registerEntity(entityTypeName, entity, entityConfig.get(DeploymentConfiguration.NAME_ATTRIBUTE_NAME));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private MultiTreeMap getDeploymentConfiguration() {
        MultiTreeMap deploymentConfiguration = new MultiTreeMap();
        return deploymentConfiguration;
    }

    public void startServer() {
        try {
            Node node = Node.getInstance();
            Registry registry = LocateRegistry.createRegistry(1099);
            registry.rebind("Node", node);
            System.out.println("Node server started");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void listAgents() {
        System.out.println("List of agents:");
        for (String agent : agents) {
            System.out.println(agent);
        }
    }
}