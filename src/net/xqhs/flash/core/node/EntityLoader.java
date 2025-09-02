package net.xqhs.flash.core.node;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import net.xqhs.flash.core.DeploymentConfiguration;
import net.xqhs.flash.core.Entity;
import net.xqhs.flash.core.Loader;
import net.xqhs.flash.core.SimpleLoader;
import net.xqhs.flash.core.util.ClassFactory;
import net.xqhs.flash.core.util.MultiTreeMap;
import net.xqhs.flash.core.util.PlatformUtils;
import net.xqhs.util.logging.Logger;

public class EntityLoader {
    private static final String NAMESEP = DeploymentConfiguration.NAME_SEPARATOR;
    private final Map<String, Map<String, List<Loader<?>>>> loaders;
    private final Loader<?> defaultLoader;
    private final Map<String, Entity<?>> loaded;
	private final ClassFactory classFactory;

	public EntityLoader(Map<String, Map<String, List<Loader<?>>>> loaders, Map<String, Entity<?>> loaded,
			Logger logger) {
//        setUnitName("EntityLoader");
//        setLoggerType(PlatformUtils.platformLogType());
        this.loaders = loaders;
        this.loaded = loaded;
		this.classFactory = PlatformUtils.getClassFactory();

		// Initialize the default loader
		this.defaultLoader = new SimpleLoader();
		this.defaultLoader.configure(null, logger, this.classFactory);
    }

    /**
     * Loads an entity for a given node configuration.
     *
     * @param node              the Node instance to which the entity is added.
     * @param nodeConfiguration the node configuration.
     * @param entityConfiguration the entity configuration.
     */
    public void loadEntity(Node node, MultiTreeMap nodeConfiguration, MultiTreeMap entityConfiguration) {
        // Get basic attributes from the configuration
        String name = entityConfiguration.getFirstValue(DeploymentConfiguration.NAME_ATTRIBUTE_NAME);
        String kind = null, id = null, cp = entityConfiguration.get(SimpleLoader.CLASSPATH_KEY);
        String local_id = entityConfiguration.getSingleValue(DeploymentConfiguration.LOCAL_ID_ATTRIBUTE);

        // Parse kind and id from the name
        if (name != null && name.contains(NAMESEP)) {
            kind = name.split(NAMESEP)[0];
            id = name.split(NAMESEP, 2)[1];
        }

        if (kind == null || kind.isEmpty()) {
            kind = entityConfiguration.get(DeploymentConfiguration.KIND_ATTRIBUTE_NAME);
        }

        if (id == null || id.isEmpty()) {
            id = entityConfiguration.get(DeploymentConfiguration.NAME_ATTRIBUTE_NAME);
            if (id == null) {
                id = name;
            }
        }

        // Adjust the entity configuration if the name contains kind:id
        if (name != null && name.contains(NAMESEP) && id != null) {
            entityConfiguration.addFirst(DeploymentConfiguration.NAME_ATTRIBUTE_NAME, id);
        }

        // Check if it's a composite agent and handle it accordingly
        if (name != null && name.startsWith("agent/composite/")) {
            processAgentComposite(node, nodeConfiguration, entityConfiguration, name);
        } else {
            // Locate the appropriate loader
            List<Loader<?>> loaderList = findLoaderForEntity(nodeConfiguration, kind, id);

            // Prepare context and subordinate entities
            List<Entity.EntityProxy<?>> context = prepareEntityContext(entityConfiguration);
            List<MultiTreeMap> subEntities = DeploymentConfiguration.filterContext(null, local_id);

            // Attempt to load the entity with the appropriate loader
            Entity<?> entity = loadEntityWithLoader(loaderList, entityConfiguration, context, subEntities);

            // If loading with the specific loader failed, try the default loader
            if (entity == null) {
                cp = Loader.autoFind(null, null, cp, kind, id, nodeConfiguration.getFirstValue("category"), null);
                if (cp != null) {
                    entityConfiguration.addFirstValue(SimpleLoader.CLASSPATH_KEY, cp);
                    if (defaultLoader.preload(entityConfiguration, context)) {
                        entity = defaultLoader.load(entityConfiguration, context, subEntities);
                    }
                }
            }

            // Register the loaded entity if successful
            if (entity != null) {
                loaded.put(local_id, entity);
                node.registerEntity(nodeConfiguration.getFirstValue("category"), entity, id);
            }
        }
    }

    /**
     * Processes a composite agent by splitting its name and handling it accordingly.
     *
     * @param node              the Node instance to which the entity is added.
     * @param nodeConfiguration the node configuration.
     * @param entityConfiguration the entity configuration.
     * @param name              the composite agent name.
     */
    private void processAgentComposite(Node node, MultiTreeMap nodeConfiguration, MultiTreeMap entityConfiguration, String name) {
        try {
            // Extract the agent name from the composite name (e.g., "agent/composite/AgentA")
            String agentName = name.substring("agent/composite/".length());

            // Add the agent to the entity configuration
            entityConfiguration.addFirst(DeploymentConfiguration.NAME_ATTRIBUTE_NAME, agentName);

            // Example of adding additional attributes for the agent (if needed)
            entityConfiguration.addFirst("agent/composite", "true");

            // Load the agent using the loaders
            List<Loader<?>> loaderList = findLoaderForEntity(nodeConfiguration, "agent", agentName);
            List<Entity.EntityProxy<?>> context = prepareEntityContext(entityConfiguration);
            List<MultiTreeMap> subEntities = DeploymentConfiguration.filterContext(null, null);

            // Try loading the agent with the appropriate loader
            Entity<?> entity = loadEntityWithLoader(loaderList, entityConfiguration, context, subEntities);

            // If loading fails, try the default loader
            if (entity == null) {
                String cp = Loader.autoFind(null, null, null, "agent", agentName, nodeConfiguration.getFirstValue("category"), null);
                if (cp != null) {
                    entityConfiguration.addFirstValue(SimpleLoader.CLASSPATH_KEY, cp);
                    if (defaultLoader.preload(entityConfiguration, context)) {
                        entity = defaultLoader.load(entityConfiguration, context, subEntities);
                    }
                }
            }

            // Register the loaded agent if successful
            if (entity != null) {
                loaded.put(agentName, entity);
                node.registerEntity(nodeConfiguration.getFirstValue("category"), entity, agentName);
            }

        } catch (Exception e) {
            System.err.println("Error processing agent composite: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Finds the loader for the entity from the loaders map.
     *
     * @param nodeConfiguration the node configuration.
     * @param kind              the kind of the entity.
     * @param id                the id of the entity.
     * @return a list of loaders for the entity.
     */
    private List<Loader<?>> findLoaderForEntity(MultiTreeMap nodeConfiguration, String kind, String id) {
        List<Loader<?>> loaderList = null;
        String catName = nodeConfiguration.getFirstValue("category");

        // Check the loaders map for a suitable loader
        if (loaders.containsKey(catName)) {
            if (loaders.get(catName).containsKey(kind)) {
                loaderList = loaders.get(catName).get(kind);
            } else if (loaders.get(catName).containsKey(null)) {
                loaderList = loaders.get(catName).get(null);
            }
        }

        return loaderList;
    }

    /**
     * Prepares the context for entity loading.
     *
     * @param entityConfiguration the entity configuration.
     * @return the list of entity proxies in context.
     */
    private List<Entity.EntityProxy<?>> prepareEntityContext(MultiTreeMap entityConfiguration) {
        List<Entity.EntityProxy<?>> context = new LinkedList<>();
        if (entityConfiguration.isSimple(DeploymentConfiguration.CONTEXT_ELEMENT_NAME)) {
            for (String contextItem : entityConfiguration.getValues(DeploymentConfiguration.CONTEXT_ELEMENT_NAME)) {
                if (loaded.containsKey(contextItem) && loaded.get(contextItem).asContext() != null) {
                    context.add(loaded.get(contextItem).asContext());
                }
            }
        }
        return context;
    }

    /**
     * Attempts to load an entity using the provided loader list.
     *
     * @param loaderList         the list of loaders to try.
     * @param entityConfiguration the entity configuration.
     * @param context            the context of the entity.
     * @param subEntities       the list of subordinate entities.
     * @return the loaded entity or null if loading failed.
     */
    private Entity<?> loadEntityWithLoader(List<Loader<?>> loaderList, MultiTreeMap entityConfiguration,
                                           List<Entity.EntityProxy<?>> context, List<MultiTreeMap> subEntities) {
        Entity<?> entity = null;
        if (loaderList != null) {
            for (Loader<?> loader : loaderList) {
                if (loader.preload(entityConfiguration, context)) {
                    entity = loader.load(entityConfiguration, context, subEntities);
                }
                if (entity != null) break;
            }
        }
        return entity;
    }
}
