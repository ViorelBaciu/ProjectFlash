package net.xqhs.flash.core.node;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import net.xqhs.flash.core.CategoryName;
import net.xqhs.flash.core.DeploymentConfiguration;
import net.xqhs.flash.core.Entity;
import net.xqhs.flash.core.Entity.EntityProxy;
import net.xqhs.flash.core.Loader;
import net.xqhs.flash.core.SimpleLoader;
import net.xqhs.flash.core.util.ClassFactory;
import net.xqhs.flash.core.util.MultiTreeMap;
import net.xqhs.flash.core.util.PlatformUtils;
import net.xqhs.util.logging.Logger;
import net.xqhs.util.logging.Unit;

public class DynamicLoader extends Unit {
	private Map<String, Entity<?>> loadedEntities;
	private final ClassFactory classFactory;
	private MultiTreeMap deploymentConfig;
	private String nodeLocalId;
	private Map<String, Map<String, List<Loader<?>>>> configuredLoaders;
	private List<MultiTreeMap> allEntities;
	private Logger logger;
	private static final String NAMESEP = DeploymentConfiguration.NAME_SEPARATOR;

	public DynamicLoader(Map<String, Entity<?>> loaded, MultiTreeMap deploymentConfig, String nodeLocalId,
			Logger logger, Map<String, Map<String, List<Loader<?>>>> configuredLoaders,
			List<MultiTreeMap> allEntities) {
		this.logger = logger;
		this.loadedEntities = loaded;
		this.classFactory = PlatformUtils.getClassFactory();
		this.deploymentConfig = deploymentConfig;
		this.nodeLocalId = nodeLocalId;
		this.configuredLoaders = configuredLoaders;
		this.allEntities = allEntities;
	}

	public void configureAndLoadLoader(MultiTreeMap loaderConfig) {
		String NAMESEP = DeploymentConfiguration.NAME_SEPARATOR;
		String name = loaderConfig.getFirstValue(DeploymentConfiguration.NAME_ATTRIBUTE_NAME);
		String entity = null, kind = null;

		if (name.contains(NAMESEP)) {
			entity = name.split(NAMESEP)[0];
			kind = name.split(NAMESEP, 2)[1];
		} else {
			entity = name;
		}

		if (entity == null || entity.length() == 0) {
			le("Loader name parsing failed for []", name);
			return;
		}

		String cp = loaderConfig.getDeepValue(name, SimpleLoader.CLASSPATH_KEY);
		List<String> packages = deploymentConfig.getValues(CategoryName.PACKAGE.s());
		cp = Loader.autoFind(classFactory, packages, cp, entity, kind, CategoryName.LOADER.s(), new LinkedList<>());

		if (cp == null) {
			le("Class for loader [] can not be found.", name);
		} else {
			try {
				Loader<?> loader = (Loader<?>) classFactory.loadClassInstance(cp, null, true);
				if (!configuredLoaders.containsKey(entity)) {
					configuredLoaders.put(entity, new LinkedHashMap<>());
				}
				if (!configuredLoaders.get(entity).containsKey(kind)) {
					configuredLoaders.get(entity).put(kind, new LinkedList<>());
				}
				configuredLoaders.get(entity).get(kind).add(loader);

				loaderConfig.getFirstTree(name).addAll(CategoryName.PACKAGE.s(), packages);
				loader.configure(loaderConfig.getFirstTree(name), logger, classFactory);
				li("Loader for [] of kind [] successfully loaded from [].", entity, kind, cp);
			} catch (Exception e) {
				le("Loader loading failed for []: ", name, PlatformUtils.printException(e));
			}
		}
	}

	/**
	 * Codul de incarcare a agentilor (entitatilor).
	 * 
	 * @param agentConfig Configurare pentru agent.
	 */
	public void loadNewAgent(MultiTreeMap agentConfig) {
		String NAMESEP = DeploymentConfiguration.NAME_SEPARATOR;
		List<String> checkedPaths = new LinkedList<>();
		String name = agentConfig.getFirstValue(DeploymentConfiguration.NAME_ATTRIBUTE_NAME);
		String kind = null, id = null, cp = agentConfig.get(SimpleLoader.CLASSPATH_KEY);
		String local_id = agentConfig.getSingleValue(DeploymentConfiguration.LOCAL_ID_ATTRIBUTE);
		String catName = CategoryName.AGENT.s();

		if (name != null && name.contains(NAMESEP)) {
			kind = name.split(NAMESEP)[0];
			id = name.split(NAMESEP, 2)[1];
		}

		if (kind == null || kind.length() == 0) {
			if (agentConfig.isSimple(DeploymentConfiguration.KIND_ATTRIBUTE_NAME)) {
				kind = agentConfig.get(DeploymentConfiguration.KIND_ATTRIBUTE_NAME);
			}
		}
		if (id == null || id.length() == 0) {
			if (agentConfig.isSimple(DeploymentConfiguration.NAME_ATTRIBUTE_NAME)) {
				id = agentConfig.get(DeploymentConfiguration.NAME_ATTRIBUTE_NAME);
			}
			if (id == null) {
				id = name;
			}
		}

		if (name != null && name.contains(NAMESEP) && id != null) {
			agentConfig.addFirst(DeploymentConfiguration.NAME_ATTRIBUTE_NAME, id);
		}

		List<Loader<?>> loaderList = null;
		if (configuredLoaders.containsKey(catName) && !configuredLoaders.get(catName).isEmpty()) {
			if (configuredLoaders.get(catName).containsKey(kind)) {
				loaderList = configuredLoaders.get(catName).get(kind);
			} else if (configuredLoaders.get(catName).containsKey(null)) {
				loaderList = configuredLoaders.get(catName).get(null);
			} else {
				loaderList = configuredLoaders.get(catName).values().iterator().next();
			}
		}

		List<EntityProxy<?>> context = new LinkedList<>();
		if (agentConfig.isSimple(DeploymentConfiguration.CONTEXT_ELEMENT_NAME)) {
			for (String contextItem : agentConfig.getValues(DeploymentConfiguration.CONTEXT_ELEMENT_NAME)) {
				if (loadedEntities.containsKey(contextItem)) {
					if (loadedEntities.get(contextItem).asContext() != null) {
						context.add(loadedEntities.get(contextItem).asContext());
					}
				} else {
					lw("Context item [] for [] [] not found as a loaded entity.", contextItem, catName, name);
				}
			}
		}

		List<MultiTreeMap> subEntities = DeploymentConfiguration.filterContext(allEntities, local_id);
		Entity<?> entity = null;

		if (loaderList != null && !loaderList.isEmpty()) {
			for (Loader<?> loader : loaderList) {
				if (loader.preload(agentConfig, context)) {
					entity = loader.load(agentConfig, context, subEntities);
				}
				if (entity != null) {
					break;
				}
			}
		}

		if (entity == null) {
			cp = Loader.autoFind(classFactory, deploymentConfig.getValues(CategoryName.PACKAGE.s()), cp, kind, id,
					catName, checkedPaths);
			if (cp == null) {
				le("Class for [] [] can not be found; tried paths ", catName, name, checkedPaths);
			} else {
				Loader<?> defaultLoader = new SimpleLoader();
				defaultLoader.configure(null, logger, classFactory);
				agentConfig.addFirstValue(SimpleLoader.CLASSPATH_KEY, cp);
				if (defaultLoader.preload(agentConfig, context)) {
					entity = defaultLoader.load(agentConfig, context, subEntities);
				}
			}
		}

		if (entity != null) {
			loadedEntities.put(local_id, entity);
			if (loadedEntities.containsKey(nodeLocalId)) {
				Node node = (Node) loadedEntities.get(nodeLocalId);
				node.registerEntity(catName, entity, id);
				li("Agent []/[] of type [] successfully loaded and registered on node [].", name, local_id, catName,
						node.getName());
			} else {
				le("Node with ID [] not found. Agent not registered.", nodeLocalId);
			}
		} else {
			le("Could not load agent []/[] of type [].", name, local_id, catName);
		}
	}

}
