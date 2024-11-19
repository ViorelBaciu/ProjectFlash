/*******************************************************************************
 * Copyright (C) 2021 Andrei Olaru.
 * 
 * This file is part of Flash-MAS. The CONTRIBUTORS.md file lists people who have been previously involved with this project.
 * 
 * Flash-MAS is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or any later version.
 * 
 * Flash-MAS is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with Flash-MAS.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package net.xqhs.flash.core.node;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import net.xqhs.flash.core.CategoryName;
import net.xqhs.flash.core.DeploymentConfiguration;
import net.xqhs.flash.core.Entity;
import net.xqhs.flash.core.Entity.EntityIndex;
import net.xqhs.flash.core.Entity.EntityProxy;
import net.xqhs.flash.core.Loader;
import net.xqhs.flash.core.SimpleLoader;
import net.xqhs.flash.core.monitoring.CentralMonitoringAndControlEntity;
import net.xqhs.flash.core.support.MessagingPylonProxy;
import net.xqhs.flash.core.util.ClassFactory;
import net.xqhs.flash.core.util.MultiTreeMap;
import net.xqhs.flash.core.util.PlatformUtils;
import net.xqhs.util.logging.Logger;
import net.xqhs.util.logging.Unit;

import javax.naming.Context;

/**
 * The {@link NodeLoader} class manages the loading of one node in the system (normally, there is one node per machine,
 * therefore this manages booting FLASH-MAS on the current machine). It manages settings, it loads the scenario, loads
 * the agent definitions (agents are actually created later).
 * <p>
 * After performing all initializations, it creates a {@link Node} instance that manages the actual deployment
 * execution.
 * 
 * @author Andrei Olaru
 */
public class NodeLoader extends Unit implements Loader<Node>, Serializable {
	{
		// sets logging parameters: the name of the log and the type (which is given by the current platform)
		setUnitName("boot");
		setLoggerType(PlatformUtils.platformLogType());
	}
	
	/**
	 * Loads a deployment starting from command line arguments.
	 * <p>
	 * 
	 * @param args
	 *            - the arguments received by the program.
	 * @return the {@link List} of {@link Node} instances that were loaded.
	 */

	///\ - loadDeployment() has been modified

	public List<Node> loadDeployment(List<String> args) {
		lf("Booting Flash-MAS.");
		
		
		// load settings & scenario
		DeploymentConfiguration deploymentConfiguration = null;
		try {
			deploymentConfiguration = new DeploymentConfiguration().loadConfiguration(args, true, null);
			lf("Configuration loaded");
		} catch (ConfigLockedException e) {
			le("settings were locked (shouldn't ever happen): " + PlatformUtils.printException(e));
			return null;
		}

		List<Node> nodes = new LinkedList<>();
		List<MultiTreeMap> allEntities = deploymentConfiguration.getEntityList();
		List<MultiTreeMap> nodesTrees = DeploymentConfiguration.filterCategoryInContext(allEntities,
				CategoryName.NODE.s(), null);
		if (nodesTrees == null || nodesTrees.isEmpty()) {
			le("No nodes present in the configuration.");
			return null;
		}

		for (MultiTreeMap nodeConfig : nodesTrees) {
			lf("Loading node ", EntityIndex.mockPrint(CategoryName.NODE.s(),
					nodeConfig.getFirstValue(DeploymentConfiguration.NAME_ATTRIBUTE_NAME)));
			Node node = loadNode(nodeConfig, DeploymentConfiguration.filterContext(allEntities,
							nodeConfig.getSingleValue(DeploymentConfiguration.LOCAL_ID_ATTRIBUTE)),
					DeploymentConfiguration.filterCategoryInContext(allEntities, CategoryName.DEPLOYMENT.s(), null)
							.get(0).getAValue(DeploymentConfiguration.LOCAL_ID_ATTRIBUTE));
			if (node != null) {
				nodes.add(node);
				lf("node loaded: []", node.getName());

//				// Start the server now that the node is loaded
//				for (Node nodes1 : nodes) {
//					nodes1.startServer();
//					nodes1.start();
//				}
				// Start the server now that the node is loaded
				node.startServer();


				// Start node functionality
				node.start();
			} else {
				le("node not loaded.");
			}
		}
		lf("[] nodes loaded.", Integer.valueOf(nodes.size()));
		doExit();
		return nodes;
	}
	///\ - loadDeployment() has been modified
	
	@Override
	public Node load(MultiTreeMap configuration) {
		return load(configuration, null, null);
	}
	
	/**
	 * Loads one {@link Node} instance, based on the provided configuration.
	 * 
	 * @param context
	 *            - this argument is not used; nodes don't support context.
	 * @return the {@link Node} the was loaded.
	 */
	@Override
	public Node load(MultiTreeMap nodeConfiguration, List<EntityProxy<? extends Entity<?>>> context,
			List<MultiTreeMap> subordinateEntities) {
		if(context != null && context.size() > 0)
			lw("nodes don't support context");
		return loadNode(nodeConfiguration, subordinateEntities, null);
	}
	
	/**
	 * Loads one {@link Node} instance, based on the provided configuration.
	 * 
	 * @param nodeConfiguration
	 *            - the configuration.
	 * @param subordinateEntities
	 *            - the entities that should be loaded inside the node, as specified by
	 *            {@link Loader#load(MultiTreeMap, List, List)}.
	 * @param deploymentID
	 *            - the local ID of the {@link CategoryName#DEPLOYMENT} entity, used to no issue context item errors for
	 *            the deployment itself. Can be <code>null</code>.
	 * @return the {@link Node} the was loaded.
	 */
	public Node loadNode(MultiTreeMap nodeConfiguration, List<MultiTreeMap> subordinateEntities, String deploymentID) {
		// Loader initialization
		String NAMESEP = DeploymentConfiguration.NAME_SEPARATOR;
		ClassFactory classFactory = PlatformUtils.getClassFactory();
		List<String> checkedPaths = new LinkedList<>(); // used to monitor class paths checked by autoFind.

		// ============================================================================== Get package list
		List<String> packages = nodeConfiguration.getValues(CategoryName.PACKAGE.s());

		// ============================================================================== Get loaders
		Map<String, Map<String, List<Loader<?>>>> loaders = new LinkedHashMap<>();
		MultiTreeMap loaderConfigs = nodeConfiguration.getSingleTree(CategoryName.LOADER.s());
		if (loaderConfigs != null) {
			if (!loaderConfigs.getSimpleNames().isEmpty()) // Just a warning
				lw("Simple keys from loader tree ignored: ", loaderConfigs.getSimpleNames());
			for (String name : loaderConfigs.getHierarchicalNames()) {
				String entity = null, kind = null;
				if (name.contains(NAMESEP)) {
					entity = name.split(NAMESEP)[0];
					kind = name.split(NAMESEP, 2)[1];
				} else {
					entity = name;
				}
				if (entity == null || entity.length() == 0) {
					le("Loader name parsing failed for []", name);
					continue;
				}

				// Find the implementation
				String cp = loaderConfigs.getDeepValue(name, SimpleLoader.CLASSPATH_KEY);
				cp = Loader.autoFind(classFactory, packages, cp, entity, kind, CategoryName.LOADER.s(), checkedPaths);
				if (cp == null) {
					le("Class for loader [] cannot be found; tried paths ", name, checkedPaths);
					continue;
				} else {
					try {
						// Instantiate loader
						Loader<?> loader = (Loader<?>) classFactory.loadClassInstance(cp, null, true);
						// Add to map
						loaders.computeIfAbsent(entity, k -> new LinkedHashMap<>())
								.computeIfAbsent(kind, k -> new LinkedList<>())
								.add(loader);
						// Configure
						loaderConfigs.getFirstTree(name).addAll(CategoryName.PACKAGE.s(), packages);
						loader.configure(loaderConfigs.getFirstTree(name), getLogger(), classFactory);
						li("Loader for [] of kind [] successfully loaded from [].", entity, kind, cp);
					} catch (Exception e) {
						le("Loader loading failed for []: ", name, PlatformUtils.printException(e));
					}
				}
			}
		} else {
			li("No loaders configured.");
		}

		// Use default loader if no specific loader is found
		Loader<?> defaultLoader = new SimpleLoader();
		defaultLoader.configure(null, getLogger(), classFactory);
		if (loaders.containsKey(null)) {
			if (loaders.get(null).containsKey(null) && !loaders.get(null).get(null).isEmpty()) {
				defaultLoader = loaders.get(null).get(null).get(0);
			} else if (!loaders.get(null).isEmpty()) {
				defaultLoader = loaders.get(null).values().iterator().next().get(0);
			}
		}

		// ============================================================================== Load entities
		// Node instance creation
		if (!nodeConfiguration.isSet(SimpleLoader.CLASSPATH_KEY)) {
			nodeConfiguration.addOneValue(SimpleLoader.CLASSPATH_KEY, Node.class.getCanonicalName());
		}
		String nodeCatName = CategoryName.NODE.s();
		String nodeName = nodeConfiguration.get(DeploymentConfiguration.NAME_ATTRIBUTE_NAME);
		String nodecp = Loader.autoFind(classFactory, packages, nodeConfiguration.get(SimpleLoader.CLASSPATH_KEY), null,
				null, nodeCatName, checkedPaths);
		if (nodecp == null) {
			le("Class for [] [] cannot be found; tried paths ", nodeCatName, nodeName, checkedPaths);
			return null;
		}
		nodeConfiguration.addFirstValue(SimpleLoader.CLASSPATH_KEY, nodecp);
		lf("Trying to load node using default loader [], from classpath []", defaultLoader.getClass().getName(),
				CategoryName.NODE.s(), nodecp);
		Node node = (Node) defaultLoader.load(nodeConfiguration);
		if (node == null) {
			le("Could not load [][].", nodeCatName, nodeName);
			return null;
		}

		Map<String, Entity<?>> loaded = new LinkedHashMap<>();
		String nodeLocalId = nodeConfiguration.getSingleValue(DeploymentConfiguration.LOCAL_ID_ATTRIBUTE);
		loaded.put(nodeLocalId, node);

		String toLoad = nodeConfiguration.getSingleValue(CategoryName.LOAD_ORDER.s());
		if (toLoad == null || toLoad.trim().isEmpty()) {
			li("Nothing to load");
		} else {
			lf("Loading order: ", toLoad);
			List<MessagingPylonProxy> messagingProxies = new LinkedList<>();
			for (String catName : toLoad.split(DeploymentConfiguration.LOAD_ORDER_SEPARATOR)) {
				CategoryName cat = CategoryName.byName(catName);
				List<MultiTreeMap> entities = DeploymentConfiguration.filterCategoryInContext(subordinateEntities,
						catName, null);
				if (entities.isEmpty()) {
					li("No [] entities defined.", catName);
					continue;
				}
				lf("Loading category: ", catName);

				for (MultiTreeMap entityConfig : entities) {
					String name = entityConfig.getFirstValue(DeploymentConfiguration.NAME_ATTRIBUTE_NAME);
					String kind = null, id = null, cp = entityConfig.get(SimpleLoader.CLASSPATH_KEY);
					String localId = entityConfig.getSingleValue(DeploymentConfiguration.LOCAL_ID_ATTRIBUTE);
					if (name != null && name.contains(NAMESEP)) {
						kind = name.split(NAMESEP)[0];
						id = name.split(NAMESEP, 2)[1];
					}
					if (kind == null || kind.length() == 0) {
						if (entityConfig.isSimple(DeploymentConfiguration.KIND_ATTRIBUTE_NAME)) {
							kind = entityConfig.get(DeploymentConfiguration.KIND_ATTRIBUTE_NAME);
						} else if (cat != null && cat.hasNameWithParts()) {
							kind = entityConfig.get(cat.nameParts()[0]);
						}
					}
					if (id == null || id.length() == 0) {
						if (entityConfig.isSimple(DeploymentConfiguration.NAME_ATTRIBUTE_NAME)) {
							id = entityConfig.get(DeploymentConfiguration.NAME_ATTRIBUTE_NAME);
						} else if (cat != null && cat.hasNameWithParts()) {
							id = entityConfig.get(cat.nameParts()[1]);
						}
						if (id == null) {
							id = name;
						}
					}

					// In case the kind:id format was used, we only want the name to be the id
					if (name != null && name.contains(NAMESEP) && id != null) {
						entityConfig.addFirst(DeploymentConfiguration.NAME_ATTRIBUTE_NAME, id);
					}

					// Find a loader for the entity
					List<Loader<?>> loaderList = null;
					String logCatLoad = null, logKindLoad = null;
					int logNLoader = 0;
					if (loaders.containsKey(catName) && !loaders.get(catName).isEmpty()) {
						logCatLoad = catName;
						if (loaders.get(catName).containsKey(kind)) {
							loaderList = loaders.get(catName).get(kind);
							logCatLoad = kind;
						} else {
							if (loaders.get(catName).containsKey(null)) {
								loaderList = loaders.get(catName).get(null);
								logKindLoad = "null";
							} else {
								loaderList = loaders.get(catName).values().iterator().next();
								logKindLoad = "first (" + loaders.get(catName).keySet().iterator().next() + ")";
							}
						}
					}

					// Build context
					List<EntityProxy<?>> context = new LinkedList<>();
					if (entityConfig.isSimple(DeploymentConfiguration.CONTEXT_ELEMENT_NAME)) {
						for (String contextItem : entityConfig.getValues(DeploymentConfiguration.CONTEXT_ELEMENT_NAME)) {
							if (loaded.containsKey(contextItem)) {
								if (loaded.get(contextItem).asContext() != null) {
									context.add(loaded.get(contextItem).asContext());
								}
							} else if (!contextItem.equals(deploymentID)) {
								lw("Context item [] for [] []/[]/[] not found as a loaded entity.", contextItem,
										catName, name, kind, localId);
							}
						}
					}

					// Build subordinate entities list
					List<MultiTreeMap> subEntities = DeploymentConfiguration.filterContext(subordinateEntities,
							localId);

					// Try to load the entity with a loader
					Entity<?> entity = null;
					if (loaderList != null && !loaderList.isEmpty()) {
						for (Loader<?> loader : loaderList) {
							lf("Trying to load []/[] [][] using []th loader for [][]", name, localId, catName, kind,
									Integer.valueOf(logNLoader), logCatLoad, logKindLoad);
							if (loader.preload(entityConfig, context)) {
								entity = loader.load(entityConfig, context, subEntities);
							}
							if (entity != null) {
								break;
							}
							logNLoader += 1;
						}
					}
					// If not, try to load the entity with the default loader
					if (entity == null) {
						cp = Loader.autoFind(classFactory, packages, cp, kind, id, catName, checkedPaths);
						if (cp == null) {
							le("Class for [] []/[]/[] cannot be found; tried paths ", catName, name, kind, localId,
									checkedPaths);
						} else {
							lf("Trying to load []/[] [][] using default loader [], from classpath []", name, localId,
									catName, kind, defaultLoader.getClass().getName(), cp);
							entityConfig.addFirstValue(SimpleLoader.CLASSPATH_KEY, cp);
						}
						if (defaultLoader.preload(entityConfig, context)) {
							entity = defaultLoader.load(entityConfig, context, subEntities);
						}
					}
					if (entity != null) {
						li("Entity []/[] of type [] successfully loaded.", name, localId, catName);
						entityConfig.addSingleValue(DeploymentConfiguration.LOADED_ATTRIBUTE_NAME,
								DeploymentConfiguration.LOADED_ATTRIBUTE_NAME);

						// Find messaging pylons that can be used by the Node
						EntityProxy<?> ctx = entity.asContext();
						if (ctx != null && ctx instanceof MessagingPylonProxy) {
							messagingProxies.add((MessagingPylonProxy) ctx);
						}

						loaded.put(localId, entity);
						node.registerEntity(catName, entity, id);
					} else {
						le("Could not load entity []/[] of type [].", name, localId, catName);
					}
					lf("Loaded items:", loaded.keySet());
				}
			}

			lf("Other configuration:");

			if (!messagingProxies.isEmpty()) {
				MessagingPylonProxy pylon = messagingProxies.stream().findFirst().get();
				node.addGeneralContext(pylon);

				if (nodeConfiguration.containsKey(DeploymentConfiguration.CENTRAL_NODE_KEY)) {
					// Delegate the central node and register the central monitoring and control entity in its context
					li("Node [] is central node.", node.getName());
					CentralMonitoringAndControlEntity centralEntity = new CentralMonitoringAndControlEntity(
							new MultiTreeMap().addSingleValue(DeploymentConfiguration.NAME_ATTRIBUTE_NAME,
											DeploymentConfiguration.CENTRAL_MONITORING_ENTITY_NAME)
									.addAll(DeploymentConfiguration.CENTRAL_NODE_KEY,
											nodeConfiguration.getValues(DeploymentConfiguration.CENTRAL_NODE_KEY)));
					centralEntity.addGeneralContext(pylon);
					node.registerEntity(DeploymentConfiguration.MONITORING_TYPE, centralEntity,
							DeploymentConfiguration.CENTRAL_MONITORING_ENTITY_NAME);

					lf("Entity [] of type [] registered.", DeploymentConfiguration.CENTRAL_MONITORING_ENTITY_NAME,
							DeploymentConfiguration.MONITORING_TYPE);
				}

				li("Loading node [] completed.", node.getName());
			}
		}
		return node;
	}



	/**
	 * Functionality not used.
	 */
	@Override
	public boolean configure(MultiTreeMap configuration, Logger log, ClassFactory factory) {
		return true;
	}
	
	/**
	 * Functionality not used.
	 */
	@Override
	public boolean preload(MultiTreeMap configuration) {
		return true;
	}
	
	/**
	 * Functionality not used.
	 */
	@Override
	public boolean preload(MultiTreeMap configuration, List<EntityProxy<? extends Entity<?>>> context) {
		return preload(configuration);
	}
}
