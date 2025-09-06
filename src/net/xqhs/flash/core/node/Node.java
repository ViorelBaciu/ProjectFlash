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
import java.lang.reflect.InvocationTargetException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

// import laura.AgentPingPong;
import net.xqhs.flash.core.CategoryName;
import net.xqhs.flash.core.DeploymentConfiguration;
import net.xqhs.flash.core.Entity;
import net.xqhs.flash.core.Loader;
import net.xqhs.flash.core.SimpleLoader;
import net.xqhs.flash.core.agent.AgentEvent;
import net.xqhs.flash.core.agent.AgentEvent.AgentEventType;
import net.xqhs.flash.core.agent.AgentWave;
import net.xqhs.flash.core.mobileComposite.MobileCompositeAgent;
import net.xqhs.flash.core.monitoring.CentralMonitoringAndControlEntity;
import net.xqhs.flash.core.node.clientApp.ClientAppInt;
import net.xqhs.flash.core.node.clientApp.ClientCallbackInterface;
import net.xqhs.flash.core.shard.AgentShard;
import net.xqhs.flash.core.shard.AgentShardDesignation;
import net.xqhs.flash.core.shard.ShardContainer;
import net.xqhs.flash.core.support.MessagingPylonProxy;
import net.xqhs.flash.core.support.MessagingShard;
import net.xqhs.flash.core.support.PylonProxy;
import net.xqhs.flash.core.util.ClassFactory;
import net.xqhs.flash.core.util.MultiTreeMap;
import net.xqhs.flash.core.util.OperationUtils;
import net.xqhs.flash.core.util.OperationUtils.ControlOperation;
import net.xqhs.flash.core.util.PlatformUtils;
import net.xqhs.flash.rmi.NodeCLI;
import net.xqhs.flash.rmi.NodeCLI.NodeInterface;
import net.xqhs.util.logging.Unit;
import net.xqhs.util.logging.UnitComponent;


/**
 * A {@link Node} instance embodies the presence of the framework on a machine, although multiple {@link Node} instances
 * may exist on the same machine.
 * <p>
 * There should be no "higher"-context entity than the node.
 * <p>
 * The node will stop when there are no more active entities in its registered entity list. By default, active entities
 * are the ones in {@link #DEFAULT_ACTIVE_ENTITIES}. All entities can be specified as active by passing the
 * <code>active:*</code> parameter in the configuration deployment of the node, leading to the node (most likely) never
 * stopping by default.
 * 
 * @author Andrei Olaru
 */
public class Node extends Unit implements Entity<Node>, NodeInterface, ClientAppInt, Remote, Serializable {
//	@Override
//	public Map<String, String> listEntities() {
//		return new HashMap<>();
//	}


	@Override
	public boolean stopEntity(String entityName) {
		return false;
	}

	/**
	 * Proxy for a {@link Node}.
	 */
	public class NodeProxy implements EntityProxy<Node> {
		@Override
		public String getEntityName() {
			return name;
		}
		
		/**
		 * Instructs the node to move the given serialized agent to a different node.
		 * 
		 * @param destination
		 *            - name of the destination node
		 * @param agentName
		 *            - name of the agent that wants to move
		 * @param agentData
		 *            - serialization of the agent
		 */
		public void moveAgent(String destination, String agentName, String agentData) {
			sendAgent(destination, agentName, agentData);
		}
	}
	
	/**
	 * The endpoint for messages sent between nodes.
	 */
	private static final String		SHARD_ENDPOINT				= "node";
	/**
	 * The name of the operation in which a node receives a mobile agent.
	 */
	public static final String		RECEIVE_AGENT_OPERATION		= "receive_agent";
	/**
	 * The name of the operation in which a node receives a mobile agent.
	 */
	public static final String		ACTIVE_PARAMETER_NAME		= "active";
	/**
	 * Value for the active parameter indicating that all entities are active entities.
	 */
	public static final String		ACTIVE_ALWAYS_VALUE			= "*";
	/**
	 * The default "active" entities, which keep the node running while they are running.
	 */
	public static final String[]	DEFAULT_ACTIVE_ENTITIES	= new String[] { "agent" };
	/**
	 * Global (implementation-wide) switch to kill the node when there are no more running active entities.
	 */
	public static final boolean		EXIT_ON_NO_ACTIVE_ENTITIES	= true;
	/**
	 * The time after which to perform the first check of active entities.
	 */
	public static final int			INITIAL_ACTIVE_CHECK		= 5000;
	
	/**
	 * The name of the node.
	 */
	protected String						name						= null;
	/**
	 * A collection of all entities added in the context of this node, indexed by their types.
	 */
	protected Map<String, List<Entity<?>>>	registeredEntities			= new HashMap<>();
	/**
	 * A {@link List} containing the entities added in the context of this node, in the order in which they were added.
	 */
	protected List<Entity<?>>				entityOrder					= new LinkedList<>();
	/**
	 * A {@link MessagingShard} of this node for message communication.
	 */
	protected MessagingShard				messagingShard;
	/**
	 * monitors if the messaging shard has been registered with its pylon.
	 */
	protected boolean						messagingShardRegistered	= false;
	/**
	 * An indication if this entity is running.
	 */
	private boolean							isRunning;
	/**
	 * The set of entity types considered as "active" and keeping the node from exiting.
	 */
	protected Set<String>					activeEntities				= new HashSet<>(
			Arrays.asList(DEFAULT_ACTIVE_ENTITIES));
	/**
	 * Monitors if all active entities still running.
	 */
	protected Timer							activeMonitor				= null;
	/**
	 * The pylon proxy of the node. This is used as a context for the node (and its {@link MessagingShard}) and for any
	 * mobile agents which arrive here.
	 */
	private PylonProxy						nodePylonProxy;
	protected String						serverURI					= null;					// FIXME: Remove this


	MultiTreeMap nodeConfiguration = new MultiTreeMap();

	private final Lock lock = new ReentrantLock();
	private int currentPort;
	
	private EntityLoader entityLoader;

	private DynamicLoader dynamicLoader;

	// protected Map<String, Map<String, List<Loader<?>>>> loaders;
	protected Map<String, Entity<?>> loaded;

	// Add the DeploymentConfiguration field
	private DeploymentConfiguration deploymentConfig;

	// Add a setter for the deployment config
	public void setDeploymentConfig(DeploymentConfiguration deploymentConfig) {
		this.deploymentConfig = deploymentConfig;
	}

	public void setDynamicLoader(DynamicLoader dynamicLoader) {
		this.dynamicLoader = dynamicLoader;
	}


	public void configure1(MultiTreeMap configure1) {
		this.nodeConfiguration = configure1;
	}
	/**
	 * Creates a new {@link Node} instance.
	 * 
	 * @param nodeConfiguration
	 *            the configuration of the node. Can be <code>null</code>.
	 */

	public Node(MultiTreeMap nodeConfiguration) {
		this.nodeConfiguration = nodeConfiguration;
		this.callbacks = new ArrayList<>();



		if(nodeConfiguration != null) {
			name = nodeConfiguration.get(DeploymentConfiguration.NAME_ATTRIBUTE_NAME);
			if(nodeConfiguration.containsKey(ACTIVE_PARAMETER_NAME))
				activeEntities = new HashSet<>(nodeConfiguration.getValues(ACTIVE_PARAMETER_NAME));
			this.serverURI = nodeConfiguration.get("region-server");

			if (nodeConfiguration.containsKey(NodeCLI.NODE_CLI_PARAM)) {
				new NodeCLI(new NodeInterface() {
					@Override
					public boolean stopEntity(String entityName) {
						// TODO Auto-generated method stub
						return false;
					}

					@Override
					public Map<String, String> listEntities() {
						// TODO Auto-generated method stub
						return null;
					}

				});
			}
		}
		setLoggerType(PlatformUtils.platformLogType());
		setUnitName(EntityIndex.register(CategoryName.NODE.s(), this)).lock();
		li("Active entitites:", activeEntities);

	}

	public Node(MultiTreeMap nodeConfiguration, int port) {
		this(nodeConfiguration);
		this.currentPort = port;
	}

	public MultiTreeMap getNodeConfiguration(){
		return this.nodeConfiguration;
	}

	/**
	 * Method used to register entities added in the context of this node.
	 * 
	 * @param entityType
	 *            - the type of the entity.
	 * @param entity
	 *            - a reference to the entity.
	 * @param entityName
	 *            - the name of the entity.
	 */


	protected void registerEntity(String entityType, Entity<?> entity, String entityName) {
		entityOrder.add(entity);
		if(!registeredEntities.containsKey(entityType))
			registeredEntities.put(entityType, new LinkedList<>());
		registeredEntities.get(entityType).add(entity);
		lf("registered an entity of type []. Provided name was [].", entityType, entityName);
	}
	
	/**
	 * It takes all available {@link ControlOperation} and build up for each of them a {@link JSONObject} containing
	 * relevant information.
	 * 
	 * @return - a json array indicating all details about each operation.
	 */
	protected JSONArray configureOperations() {
		JSONArray operations = new JSONArray();
		for(OperationUtils.ControlOperation op : OperationUtils.ControlOperation.values()) {
			JSONObject o = OperationUtils.operationToJSON(op.getOperation(), getName(), "", "");
			operations.add(o);
		}
		return operations;
	}
	
	/**
	 * Method used to send registration messages to {@link CentralMonitoringAndControlEntity} This lets it know what
	 * entities are in the content of current node and what operations can be performed on them.
	 *
	 * @return - an indication of success.
	 */
	protected boolean registerEntitiesToCentralEntity() {
		JSONArray operations = configureOperations();
		JSONArray entities = new JSONArray();
		registeredEntities.forEach((category, value) -> {
			for(Entity<?> entity : value) {
				JSONObject ent = OperationUtils.registrationToJSON(getName(), category, entity.getName(), operations);
				entities.add(ent);
			}
		});
		return sendMessage(DeploymentConfiguration.CENTRAL_MONITORING_ENTITY_NAME, entities.toString());
	}

	@Override
	public boolean start() {

//		StartServer startServer = new StartServer(nodeConfiguration);
//		startServer.startServer();

		li("Starting node [] with entities [].", name, entityOrder);
		for(Entity<?> entity : entityOrder) {
			String entityName = entity.getName();
			lf("starting entity []...", entityName);
			if(entity.start()) {
				lf("entity [] started successfully.", entityName);
				EntityProxy<?> ctx = entity.asContext();
				if(!messagingShardRegistered && getName() != null && messagingShard != null
						&& (ctx instanceof MessagingPylonProxy)) {
					messagingShard.register(getName());
					messagingShardRegistered = true;
				}
			}
			else
				le("failed to start entity [].", entityName);
		}
		isRunning = true;
		if(messagingShard != null)
			messagingShard.signalAgentEvent(new AgentEvent(AgentEventType.AGENT_START));
		sendStatusUpdate();
		li("Node [] started.", name);
		
		if(getName() != null && registerEntitiesToCentralEntity())
			lf("Entities successfully registered to control entity.");

		Timer timer = new Timer();
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				try {

					StartServer startServer = new StartServer(nodeConfiguration);
					startServer.startServer();

//					String[] argset = ("-node nodeA -pylon local: -agent agentC2 -shard messaging classpath:AgentPingPong sendTo:agentB2")
////							-node nodeA -pylon local: -agent composite:agentDX -shard messaging par:val -shard EchoTesting -agent agentCV parameter:one
//							.split(" ");
//
//
//					MultiTreeMap rootTree = getRootTree();
//					if (rootTree == null) {
//						rootTree = new MultiTreeMap();
//						setRootTree(rootTree);
//					}
//
//					DeploymentConfiguration deploymentConfig = new DeploymentConfiguration();
//					deploymentConfig.readCLIArgs(Arrays.asList(argset).iterator(),
//							new DeploymentConfiguration.CtxtTriple(CategoryName.DEPLOYMENT.s(), null, rootTree),
//							rootTree, new LinkedList<>(), new HashMap<>(), new UnitComponent("ClientApp"));
					// this.registerEntity(argset, messagingShard, argset);

					// System.out.println("Agents added successfully in rootTree: " + rootTree);

				} catch (Exception e) {
					System.err.println("Error in TimerTask: " + e.getMessage());
					e.printStackTrace();
				}
			}
		}, 10);



		return true;
	}

//	@Override
//	public void addAgentTree(String command) throws RemoteException {
//		try {
//			String[] args = command.split("\\s+");
//
//			MultiTreeMap rootTree = getRootTree();
//			if (rootTree == null) {
//				rootTree = new MultiTreeMap();
//				setRootTree(rootTree);
//			}
//			DeploymentConfiguration deploymentConfig = new DeploymentConfiguration();
//			deploymentConfig.readCLIArgs(Arrays.asList(args).iterator(),
//					new DeploymentConfiguration.CtxtTriple(CategoryName.DEPLOYMENT.s(), null, rootTree), rootTree,
//					new LinkedList<>(), new HashMap<>(), new UnitComponent("ClientApp"));
//
//			System.out.println("Agents added successfully in rootTree: " + rootTree);
//		} catch (Exception e) {
//			System.err.println("Error adding agents: " + e.getMessage());
//			e.printStackTrace();
//		}
//	}


	@Override

	public void addAgentTree(String command) throws RemoteException {
		try {

			System.out.println("Entitățile active actuale pentru acest nod sunt: " + this.activeEntities);
			// 1. Parsare comanda și extragere configurație agent
			String[] args = command.split("\\s+");
			MultiTreeMap tempConfig = new MultiTreeMap();
//			this.deploymentConfig.readCLIArgs(Arrays.asList(args).iterator(),
//					new DeploymentConfiguration.CtxtTriple(CategoryName.AGENT.s(), null, tempConfig), tempConfig,
//					new LinkedList<>(), new HashMap<>(), new UnitComponent("ClientApp"));
			this.deploymentConfig.readCLIArgs(Arrays.asList(args).iterator(),
					new DeploymentConfiguration.CtxtTriple(CategoryName.NODE.s(), null, tempConfig), tempConfig,
					new LinkedList<>(), new HashMap<>(), new UnitComponent("ClientApp"));
			// Extrage corect arborele de configurare al agentului

			// MultiTreeMap agentCategoryTree =
			// tempConfig.getSingleTree(CategoryName.AGENT.s());
//			if (agentCategoryTree == null) {
//				le("Failed to extract agent configuration from command: " + command);
//			}
			// nodeConfiguration.getDeepValue("agent", names);
			MultiTreeMap agentCategoryTree2 = tempConfig.getSingleTree(CategoryName.NODE.s());
			// nodeConfiguration.getTrees("nodeB");
			nodeConfiguration.addOneTree("agent", agentCategoryTree2);
			// nodeConfiguration.addOneTreeGet("name", agentCategoryTree2);

			MultiTreeMap s2 = nodeConfiguration.getSingleTree(CategoryName.NODE.s());

			MultiTreeMap agentCategoryTree22 = tempConfig.getSingleTree(CategoryName.AGENT.s());
			nodeConfiguration.addOneTreeGet("local:", agentCategoryTree22);
			System.out.println(this.getNodeConfiguration());
			MultiTreeMap agentTree = null;
//			for (String key : tempConfig.getHierarchicalNames()) {
//				// Folosește getFirstTree() pentru a obține primul sub-arbore cu acel nume
//				agentTree = tempConfig.getFirstTree(key);
//				if (agentTree != null) {
//					break; // Am găsit agentul, ieșim din buclă.
//				}
//			}

			// 2. Adaugă configurația agentului la arborele nodului
//			this.getNodeConfiguration().addAgentToRootTree(
//					agentTree.getFirstValue(DeploymentConfiguration.NAME_ATTRIBUTE_NAME), agentTree,
//					messagingShardRegistered, isRunning());

			// 3. Obține loader-ul implicit
			// Aici este logica din getDefaultLoader()
			Map<String, Map<String, List<Loader<?>>>> loaders = new LinkedHashMap<>();

			MultiTreeMap agentCategoryTree = tempConfig.getSingleTree(CategoryName.AGENT.s());

			// MultiTreeMap test1 = nodeConfiguration.getFirstTree(CategoryName.PYLON.s());
			// MultiTreeMap test2 = nodeConfiguration.getSingleTree(CategoryName.AGENT.s());
			String toLoad2 = nodeConfiguration.getSingleValue(CategoryName.LOAD_ORDER.s());
			// String toLoad3 = nodeConfiguration.getSingleValue(CategoryName.PYLON.s());
			List<String> toLoad4 = nodeConfiguration.getValues(CategoryName.PACKAGE.s());


			Loader<?> defaultLoader = new SimpleLoader();
			defaultLoader.configure(null, getLogger(), PlatformUtils.getClassFactory());
			ClassFactory classFactory = PlatformUtils.getClassFactory();
			List<String> packages = nodeConfiguration.getValues(CategoryName.PACKAGE.s());

			agentCategoryTree22.addAll("package", packages);
			agentCategoryTree22.addSingleValue("load_order", toLoad2);
			nodeConfiguration.addAll("package", packages);
			nodeConfiguration.addSingleValue("load_order", toLoad2);
			// this.registerEntity(CategoryName.AGENT.s(), null, toLoad2);
			// Configurezi ShardMessageing
			// this.registerEntitiesToCentralEntity();

			// ---- nu va merge ca nu ai context
			Entity<?> newAgent = defaultLoader.load(agentCategoryTree22, null, null);
			// this.configure1(nodeConfiguration);
			Node node = (Node) defaultLoader.load(nodeConfiguration);
			Map<String, Entity<?>> loaded = new LinkedHashMap<>();

			String NAMESEP = DeploymentConfiguration.NAME_SEPARATOR;
			for (String agentName : agentCategoryTree.getHierarchicalNames()) {
				// MultiTreeMap agentTree = null;
				for (String key : agentCategoryTree.getHierarchicalNames()) {
					// Folosește getFirstTree() pentru a obține primul sub-arbore cu acel nume
					agentTree = agentCategoryTree.getFirstTree(key);
					String entity = null, kind = null;
					if (name.contains(NAMESEP)) {
						entity = name.split(NAMESEP)[0];
						kind = name.split(NAMESEP, 2)[1];
					}
					if (agentTree != null) {
						break;
					}

					continue;
				}

				String toLoad = nodeConfiguration.getSingleValue(CategoryName.LOAD_ORDER.s());
				for (String catName : toLoad.split(DeploymentConfiguration.LOAD_ORDER_SEPARATOR)) {
					CategoryName cat = CategoryName.byName(catName);
//			List<MultiTreeMap> entities = DeploymentConfiguration.filterCategoryInContext(agentTree,
//					catName, null);
					String kind = null, id = null;
					if (name != null && name.contains(NAMESEP)) { // if name is can be split, split it into kind and id
						kind = name.split(NAMESEP)[0];
						id = name.split(NAMESEP, 2)[1];
					}
					if (kind == null || kind.length() == 0) {
						if (agentTree.isSimple(DeploymentConfiguration.KIND_ATTRIBUTE_NAME))
							kind = agentTree.get(DeploymentConfiguration.KIND_ATTRIBUTE_NAME);
						else if (cat != null && cat.hasNameWithParts())
							kind = agentTree.get(cat.nameParts()[0]);
				}
				List<Loader<?>> loaderList = null;
				String log_catLoad = null, log_kindLoad = null;
				int log_nLoader = 0;
				if (loaders.containsKey(catName) && !loaders.get(catName).isEmpty()) {
					// if the category in loader list
					log_catLoad = catName;
					if (loaders.get(catName).containsKey(kind)) { // get loaders for this kind
						loaderList = loaders.get(catName).get(kind);
						log_catLoad = kind;
					} else { // if no loaders for this kind
						if (loaders.get(catName).containsKey(null)) {// get the null kind
							loaderList = loaders.get(catName).get(null);
							log_kindLoad = "null";
						} else { // get loaders for the first kind
							loaderList = loaders.get(catName).values().iterator().next();
							log_kindLoad = "first (" + loaders.get(catName).keySet().iterator().next() + ")";
						}
					}
				}


				String node_local_id = nodeConfiguration.getSingleValue(DeploymentConfiguration.LOCAL_ID_ATTRIBUTE);
				loaded.put(node_local_id, node);
				List<EntityProxy<?>> context = new LinkedList<>();
				if (this.getNodeConfiguration().isSimple(DeploymentConfiguration.CONTEXT_ELEMENT_NAME))
					for (String contextItem : this.getNodeConfiguration()
							.getValues(DeploymentConfiguration.CONTEXT_ELEMENT_NAME))
						if (loaded.containsKey(contextItem)) {
							if (loaded.get(contextItem).asContext() != null)
								context.add(loaded.get(contextItem).asContext());
						} else if (!contextItem.equals(null))
							lw("Context item [] for [] []/[]/[] not found as a loaded entity.", contextItem, catName,
									name, kind, null);

				String local_id = agentTree.getSingleValue(DeploymentConfiguration.LOCAL_ID_ATTRIBUTE);
				Entity<?> entity = null;
				if (loaderList != null && !loaderList.isEmpty())
					for (Loader<?> loader : loaderList) { // try loading
						lf("Trying to load []/[] [][] using []th loader for [][]", name, local_id, catName, kind,
								Integer.valueOf(log_nLoader), log_catLoad, log_kindLoad);
						if (loader.preload(agentCategoryTree22, context))
							entity = loader.load(agentCategoryTree22, context, null);
						if (entity != null)
							break;
						log_nLoader += 1;
					}

			}

			// 5. Încarcă agentul

			String kind = agentTree.getFirstValue(DeploymentConfiguration.KIND_ATTRIBUTE_NAME);
			String name = agentTree.getFirstValue(DeploymentConfiguration.NAME_ATTRIBUTE_NAME);
			String cp = Loader.autoFind(classFactory, packages, agentTree.get(SimpleLoader.CLASSPATH_KEY), kind, name,
					CategoryName.AGENT.s(), new LinkedList<>());
			String local_id = agentTree.getSingleValue(DeploymentConfiguration.LOCAL_ID_ATTRIBUTE);
			if (cp != null) {
				agentTree.addFirstValue(SimpleLoader.CLASSPATH_KEY, cp);
			}

			if (defaultLoader.preload(agentTree, null)) {
				newAgent = defaultLoader.load(agentTree, null, null);
			}


			// 6. Înregistrează și pornește noul agent
			if (newAgent != null) {
				node.registerEntity(CategoryName.AGENT.s(), newAgent, newAgent.getName());
//				this.loaded.put(this.getNodeConfiguration().getSingleValue(DeploymentConfiguration.LOCAL_ID_ATTRIBUTE),
//						newAgent);
				// this.loaded.put(this.getNodeConfiguration().getSingleValue(local_id), node);
				node.registerEntity(CategoryName.AGENT.s(), newAgent, newAgent.getName());

				if (!newAgent.isRunning()) {
					if (newAgent.start()) {
						li("New agent [" + newAgent.getName() + "] started successfully.");
					} else {
						le("Failed to start new agent [" + newAgent.getName() + "].");
					}
				}
				li("Entity [" + newAgent.getName() + "] of type [" + CategoryName.AGENT.s()
						+ "] successfully loaded and registered.");
			} else {
				le("Could not load agent from command: " + command);
			}
			System.out.println(node.registeredEntities);

		}


		} catch (Exception e) {
			System.err.println("Error adding agents: " + e.getMessage());
			e.printStackTrace();
		}
	}

	@Override
	public Map<String, String> listEntities() {
		Map<String, String> entityStatusMap = new HashMap<>();
		for (Entity<?> entity : entityOrder) {
			// You'll need to get the status from your Entity object.
			// Assuming your Entity class has an isRunning() method.
			String status = entity.isRunning() ? "running" : "stopped";
			entityStatusMap.put(entity.getName(), status);
		}
		return entityStatusMap;
	}

	@Override
	public boolean stop() {

		li("Stopping node [] with entities [].", name, entityOrder);
		LinkedList<Entity<?>> reversed = new LinkedList<>(entityOrder);
		Collections.reverse(reversed);
		for(Entity<?> entity : reversed) {
			if(entity.isRunning()) {
				lf("stopping an entity...");
				if(entity.stop())
					lf("entity stopped successfully.");
				else
					le("failed to stop entity.s");
			}
		}
		isRunning = false;
		sendStatusUpdate();
		li("Node [] stopped.", name);
		return true;
	}
	
	@Override
	public boolean isRunning() {
		return isRunning;
	}
	
	@Override
	public String getName() {
		return name;
	}
	
	@Override
	public boolean addContext(EntityProxy<Node> context) {
		return addGeneralContext(context);
	}
	
	@Override
	public boolean addGeneralContext(EntityProxy<? extends Entity<?>> context) {
		if(nodePylonProxy != null)
			// only one pylon can be added as context for the node
			return false;
		PylonProxy pylonProxy = (PylonProxy) context;
		nodePylonProxy = pylonProxy;
		String recommendedShard = pylonProxy.getRecommendedShardImplementation(
				AgentShardDesignation.standardShard(AgentShardDesignation.StandardAgentShard.MESSAGING));
		try {
			messagingShard = (MessagingShard) PlatformUtils.getClassFactory().loadClassInstance(recommendedShard, null,
					true);
		} catch(ClassNotFoundException | InstantiationException | NoSuchMethodException | IllegalAccessException
				| InvocationTargetException e) {
			le("Unable to construct node messaging shard: ", PlatformUtils.printException(e));
		}
		messagingShard.addContext(new ShardContainer() {
			@Override
			public boolean postAgentEvent(AgentEvent event) {
				switch(event.getType()) {
				case AGENT_WAVE:
					String localAddr = ((AgentWave) event).getCompleteDestination();
					if(!(localAddr.split(AgentWave.ADDRESS_SEPARATOR)[0]).equals(getName()))
						break;
					JsonObject msg = new Gson().fromJson(((AgentWave) event).getContent(), JsonObject.class);
					if(msg == null)
						break;
					parseReceivedMsg(msg);
					break;
				default:
					break;
				}
				return true; // FIXME it always returns true
			}
			
			@Override
			public AgentShard getAgentShard(AgentShardDesignation designation) {
				// no other shards in this container (in the node)
				return null;
			}
			
			@Override
			public String getEntityName() {
				return getName();
			}
		});
		// FIXME: remove this protocol-specific code
		messagingShard.configure(
				new MultiTreeMap().addSingleValue("connectTo", this.serverURI).addSingleValue("agent_name", getName()));
		lf("Messaging shard added, affiliated with pylon []", pylonProxy.getEntityName());
		return messagingShard.addGeneralContext(context);
	}
	
	@Override
	public boolean removeGeneralContext(EntityProxy<? extends Entity<?>> context) {
		if(nodePylonProxy != context)
			return false;
		nodePylonProxy = null;
		messagingShard = null;
		return true;
	}
	
	@Override
	public boolean removeContext(EntityProxy<Node> context) {
		// unsupported
		return false;
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public EntityProxy<Node> asContext() {
		return new NodeProxy();
	}
	
	protected void checkRunning() {
		boolean allActive = (activeEntities.size() == 1)
				&& activeEntities.iterator().next().equals(ACTIVE_ALWAYS_VALUE);
		for(String type : registeredEntities.keySet())
			if(activeEntities.contains(type) || allActive)
				for(Entity<?> e : registeredEntities.get(type))
					if(e.isRunning())
						// found an active entity still running
						return;
		li("Node [] will stop due to no more active entitites running. Active entity type list was [].", name,
				activeEntities);
		activeMonitor.cancel();
		stop();
	}
	
	/**
	 * Send a message via {@link MessagingShard}.
	 * 
	 * @param destination
	 *            - the name of the destination entity
	 * @param content
	 *            - the content to be sent
	 * @return - an indication of success
	 */
	public boolean sendMessage(String destination, String content) {
		return messagingShard.sendMessage(AgentWave.makePath(getName(), SHARD_ENDPOINT),
				AgentWave.makePath(destination, SHARD_ENDPOINT), content);
	}
	
	/**
	 * Build a {@link JSONObject} to send updates about the new status of the node.
	 * 
	 * @return - an indication of success
	 */
	private boolean sendStatusUpdate() {
		if(getName() == null)
			return false;
		String status = isRunning ? "RUNNING" : "STOPPED";
		JSONObject update = OperationUtils.operationToJSON(
				OperationUtils.MonitoringOperation.STATUS_UPDATE.getOperation(), "", status, getName());
		return sendMessage(DeploymentConfiguration.CENTRAL_MONITORING_ENTITY_NAME, update.toString());
	}
	
	/**
	 * This method parses the content received and takes further control/monitoring decisions.
	 * 
	 * @param jo
	 *            - an object representing the content received with an {@link AgentEvent}
	 */
	private void parseReceivedMsg(JsonObject jo) {
		String op = jo.get(OperationUtils.NAME).getAsString();
		if(OperationUtils.ControlOperation.START.getOperation().equals(op)) {
			String param = jo.get(OperationUtils.PARAMETERS).getAsString();
			if(param == null)
				return;
			Entity<?> entity = entityOrder.stream().filter(en -> en.getName().equals(param)).findFirst().orElse(null);
			if(entity == null) {
				le("[] entity not found in the context of [].", param, name);
				return;
			}
			if(entity.start()) {
				lf("[] was started by parent [].", param, name);
				return;
			}
		}
		else if(RECEIVE_AGENT_OPERATION.equals(op)) {
			String agentData = jo.get(OperationUtils.PARAMETERS).getAsString();
			
			MobileCompositeAgent agent = MobileCompositeAgent.deserializeAgent(agentData);
			registerEntity(CategoryName.AGENT.toString(), agent, agent.getName());
			lf("Starting agent [] after moving...", agent.getName());
			agent.addGeneralContext(asContext());
			agent.addContext(nodePylonProxy);
			agent.start();
		}
	}
	
	/**
	 * Removes the agent from the list of entities and sends it to a different node.
	 * 
	 * @param destination
	 *            - name of the destination node
	 * @param agentName
	 *            - name of the agent that wants to move
	 * @param agentData
	 *            - serialization of the agent
	 */
	protected void sendAgent(String destination, String agentName, String agentData) {
		entityOrder.stream()
				.filter(entity -> entity instanceof MobileCompositeAgent && entity.getName().equals(agentName))
				.findAny().ifPresent(entity -> entityOrder.remove(entity));
		JsonObject root = new JsonObject();
		root.addProperty(OperationUtils.NAME, Node.RECEIVE_AGENT_OPERATION);
		root.addProperty(OperationUtils.PARAMETERS, agentData);
		
		lf("Send message with agent [] to []", agentName, destination);
		sendMessage(destination, root.toString());
	}

	private List<ClientCallbackInterface> callbacks;


	public synchronized void registerCallback(ClientCallbackInterface callback) throws RemoteException{
		callbacks.add(callback);
	}

	private void notifyClients(String agentName) throws RemoteException{
		for(ClientCallbackInterface callback : callbacks){
			try{
				callback.notifyAgentAdded(agentName);
			} catch (RemoteException e){
				System.out.println("Failed to notify client: " + e.getMessage());
			}
		}
	}
	public MultiTreeMap getRootTree() {
		lock.lock();
		try {
			return nodeConfiguration.getATree("root");
		} finally {
			lock.unlock();
		}
	}

	public void setRootTree(MultiTreeMap rootTree) {
		lock.lock();
		try {
			nodeConfiguration.addAgentToRootTree("root", rootTree, false, true);
		} finally {
			lock.unlock();
		}
	}

	public void printAllAgents() {

//		MultiTreeMap rootTree = nodeConfiguration.getATree("root");
//
//		if (rootTree == null || (rootTree.getSimpleNames().isEmpty() && rootTree.getHierarchicalNames().isEmpty())) {
//			System.out.println("There are no Agents in rootTree");
//			return;
//		}
//
//		System.out.println("The list of agents from rootTree:\n");
//
//		for (String agentName : rootTree.getSimpleNames()) {
//			System.out.println("[Agent] " + agentName);
//		}
//
//		for (String agentName : rootTree.getHierarchicalNames()) {
//			MultiTreeMap subTree = rootTree.getATree(agentName);
//			if (subTree != null) {
//				System.out.println("[MainAgent] " + agentName);
//				printAgentTree(agentName, subTree, 1);
//			}
//		}

		System.out.println(this.getNodeConfiguration());
	}

	private void printAgentTree(String parentName, MultiTreeMap subTree, int level) {
		StringBuilder indent = new StringBuilder();
		for (int i = 0; i < level * 4; i++) {
			indent.append(" ");
		}
		for (String subAgent : subTree.getSimpleNames()) {
			System.out.println(indent + "├── [Sub-Agent] " + subAgent);
		}
		for (String shard : subTree.getHierarchicalNames()) {
			System.out.println(indent + "├── [Shard] " + shard);
			MultiTreeMap deeperTree = subTree.getATree(shard);
			if (deeperTree != null) {
				printAgentTree(shard, deeperTree, level + 1);
			}
		}
	}

	public void printAllAgents2() {
		MultiTreeMap rootTree = getRootTree();

		if (rootTree == null) {
			System.out.println("Nu exista agenti in configuratia nodului.");
			return;
		}

		System.out.println("Lista tuturor agentilor din nodul curent:");

		// Parcurge toate categoriile din rootTree
		for (String category : rootTree.getHierarchicalNames()) {
			if (rootTree.isHierarchical(CategoryName.AGENT.s())) {
				MultiTreeMap agentCategoryTree = rootTree.getATree(CategoryName.AGENT.s());
				printAgentTree2("Agent", agentCategoryTree, 0);
			} else {
				// Poți afișa și alte entități, dacă este necesar
				// MultiTreeMap otherCategoryTree = rootTree.getATree(category);
				// printEntities(otherCategoryTree, category);
			}
		}

		for (String agentName : rootTree.getSimpleNames()) {
			System.out.println(" - " + agentName);
		}
	}

	private void printAgentTree2(String parentName, MultiTreeMap tree, int level) {
		StringBuilder indent = new StringBuilder();
		for (int i = 0; i < level * 4; i++) {
			indent.append(" ");
		}

		// Afișează agenții de tip "simplu" (valori simple)
		for (String agentName : tree.getSimpleNames()) {
			System.out.println(indent + "├── [Simple Agent] " + agentName);
		}

		// Afișează agenții de tip "ierarhic" (sub-arbori)
		for (String subCategory : tree.getHierarchicalNames()) {
			MultiTreeMap subTree = tree.getATree(subCategory);
			if (subTree != null) {
				if (level > 0) { // Pentru sub-categorii
					System.out.println(indent + "├── [" + subCategory + "]");
				}
				printAgentTree(subCategory, subTree, level + 1);
			}
		}
	}

}
