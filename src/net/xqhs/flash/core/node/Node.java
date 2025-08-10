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

import net.xqhs.flash.core.CategoryName;
import net.xqhs.flash.core.DeploymentConfiguration;
import net.xqhs.flash.core.Entity;
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
	@Override
	public Map<String, String> listEntities() {
		return new HashMap<>();
	}


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
			
			if(nodeConfiguration.containsKey(NodeCLI.NODE_CLI_PARAM)) {
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

					@Override
					public void addAgent(String agentName, String shardName) throws RemoteException {
						Node.this.addAgent(agentName, shardName);
					}

					/*public void readCLIArgs(){
						DeploymentConfiguration.readCLIArgs(
								Arrays.asList("-additional value".split("")).iterator(),
								new DeploymentConfiguration.CtxtTriple(CategoryName.DEPLOYMENT.s(), null, nodeConfiguration),
								nodeConfiguration, new LinkedList<>(), new HashMap<>(), new UnitComponent("test")
						);
					}*/

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

	private void initializeNodeConfiguration() {

		if (nodeConfiguration != null) {
			// Set the name from the node configuration
			name = nodeConfiguration.get(DeploymentConfiguration.NAME_ATTRIBUTE_NAME);

			// Check if there are active entities configured and update the activeEntities set
			if (nodeConfiguration.containsKey(ACTIVE_PARAMETER_NAME)) {
				activeEntities = new HashSet<>(nodeConfiguration.getValues(ACTIVE_PARAMETER_NAME));
			}

			// Set the server URI from the configuration if available
			this.serverURI = nodeConfiguration.get("region-server");

			if (nodeConfiguration.containsKey(NodeCLI.NODE_CLI_PARAM)) {
				new NodeCLI(new NodeInterface() {
					@Override
					public boolean stopEntity(String entityName) {
						return false;
					}

					@Override
					public Map<String, String> listEntities() {
						return new HashMap<>();
					}

					@Override
					public void addAgent(String agentName, String shardName) throws RemoteException {
						Node.this.addAgent(agentName, shardName);
					}

				});
			}
			li("Active entities:", activeEntities);
		}
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

		StartServer startServer = new StartServer(nodeConfiguration);
		startServer.startServer();

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

					String[] argset = ("-node nodeA -pylon local: -agent composite:agentDX -shard messaging par:val -shard EchoTesting -agent agentCV parameter:one")
							.split(" ");

					MultiTreeMap rootTree = getRootTree();
					if (rootTree == null) {
						rootTree = new MultiTreeMap();
						setRootTree(rootTree);
					}
					DeploymentConfiguration deploymentConfig = new DeploymentConfiguration();
					deploymentConfig.readCLIArgs(
							Arrays.asList(argset).iterator(),
							new DeploymentConfiguration.CtxtTriple(CategoryName.DEPLOYMENT.s(), null, rootTree),
							rootTree,
							new LinkedList<>(),
							new HashMap<>(),
							new UnitComponent("ClientApp")
					);

					System.out.println("Agents added successfully in rootTree: " + rootTree);

				} catch (Exception e) {
					System.err.println("Error in TimerTask: " + e.getMessage());
					e.printStackTrace();
				}
			}
		}, 10);

		return true;
	}

	@Override
	public void addAgentTree(String command) throws RemoteException {
		try {
			String[] args = command.split("\\s+");

			MultiTreeMap rootTree = getRootTree();
			if (rootTree == null) {
				rootTree = new MultiTreeMap();
				setRootTree(rootTree);
			}
			DeploymentConfiguration deploymentConfig = new DeploymentConfiguration();
			deploymentConfig.readCLIArgs(Arrays.asList(args).iterator(),
					new DeploymentConfiguration.CtxtTriple(CategoryName.DEPLOYMENT.s(), null, rootTree), rootTree,
					new LinkedList<>(), new HashMap<>(), new UnitComponent("ClientApp"));

			System.out.println("Agents added successfully in rootTree: " + rootTree);
		} catch (Exception e) {
			System.err.println("Error adding agents: " + e.getMessage());
			e.printStackTrace();
		}
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
					le("failed to stop entity.");
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


	public void addAgent(String agentName, String shardName) throws RemoteException{
		listEntities().put(agentName,shardName);
		// Logic to add the agent to the specified shard would go here.
		System.out.println("Adding agent " + agentName + " to shard " + shardName);
		notifyClients(agentName);
	}

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

		MultiTreeMap rootTree = nodeConfiguration.getATree("root");

		if (rootTree == null || (rootTree.getSimpleNames().isEmpty() && rootTree.getHierarchicalNames().isEmpty())) {
			System.out.println("There are no Agents in rootTree");
			return;
		}

		System.out.println("The list of agents from rootTree:\n");

		for (String agentName : rootTree.getSimpleNames()) {
			System.out.println("[Agent] " + agentName);
		}

		for (String agentName : rootTree.getHierarchicalNames()) {
			MultiTreeMap subTree = rootTree.getATree(agentName);
			if (subTree != null) {
				System.out.println("[MainAgent] " + agentName);
				printAgentTree(agentName, subTree, 1);
			}
		}
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

	public void addNewAgent(String nodeName, String pylonName, String agentName, String agentType, String agentClass) {
		lock.lock();
		try {
			MultiTreeMap rootTree = getRootTree();
			if (rootTree == null) {
				rootTree = new MultiTreeMap();
				setRootTree(rootTree);
			}

			// 1. Navighează către nodul specificat
			MultiTreeMap nodeTree = rootTree.getSingleTree(CategoryName.NODE.s(), true).getATree(nodeName);
			if (nodeTree == null) {
				System.err.println("Nodul '" + nodeName + "' nu a fost gasit. Adaugarea agentului a esuat.");
				return;
			}

			// 2. Navighează către pylon-ul specificat sub acel nod
			MultiTreeMap pylonTree = nodeTree.getSingleTree(CategoryName.PYLON.s(), true).getATree(pylonName);
			if (pylonTree == null) {
				System.err.println("Pylon-ul '" + pylonName + "' nu a fost gasit sub nodul '" + nodeName
						+ "'. Adaugarea agentului a esuat.");
				return;
			}

			// 3. Obține sau creează arborele de agenți sub pylon
			MultiTreeMap agentCategoryTree = pylonTree.getSingleTree(CategoryName.AGENT.s(), true);

			// 4. Adaugă agentul
			MultiTreeMap newAgentNode = agentCategoryTree.addOneTreeGet(agentName, new MultiTreeMap());
			newAgentNode.addSingleValue("type", agentType);
			newAgentNode.addSingleValue("class", agentClass);

			lf("Noul agent '{}' a fost adaugat la configuratia nodului '{}' sub pylon-ul '{}'.", agentName, nodeName,
					pylonName);

		} finally {
			lock.unlock();
		}
	}

//	public void addNewAgent(String agentName, String agentType, String agentClass) {
//		lock.lock();
//		try {
//			MultiTreeMap rootTree = getRootTree();
//			if (rootTree == null) {
//				rootTree = new MultiTreeMap();
//				setRootTree(rootTree);
//			}
//
//			// Asigură-te că există un arbore pentru categoria "agent"
//			MultiTreeMap agentCategoryTree = rootTree.getSingleTree(CategoryName.AGENT.s(), true);
//
//			// Adaugă direct valorile noului agent sub numele acestuia
//			// Creezi un sub-arbore pentru noul agent și adaugi valorile în el.
//			MultiTreeMap newAgentNode = agentCategoryTree.addOneTreeGet(agentName, new MultiTreeMap());
//
//			// Adaugă atributele agentului în noul sub-arbore.
//			newAgentNode.addSingleValue("type", agentType);
//			newAgentNode.addSingleValue("class", agentClass);
//
//			lf("Noul agent '{}' a fost adaugat la configuratie.", agentName);
//
//		} finally {
//			lock.unlock();
//		}
//	}

//	public void addNewAgent(String command) {
//		String[] args = command.split(" ");
//		MultiTreeMap currentTree = getRootTree();
//
//		for (int i = 0; i < args.length; i += 2) {
//			String key = args[i].substring(1); // elimină '-'
//			String value = args[i + 1];
//
//			// Navighează prin arborele de configurare
//			if (currentTree.isHierarchical(key) && currentTree.getATree(key).containsKey(value)) {
//				// Dacă cheia (e.g., 'node', 'pylon') și valoarea (e.g., 'nodeA', 'local:')
//				// există,
//				// trecem la următorul nivel în arbore
//				currentTree = currentTree.getATree(key).getATree(value);
//			} else if (key.equals("agent")) {
//				// Când ajungem la cheia 'agent', adăugăm noul agent în arborele curent
//				// (e.g., în sub-arborele pylon-ului)
//				MultiTreeMap newAgentNode = new MultiTreeMap();
//				newAgentNode.addSingleValue(DeploymentConfiguration.NAME_ATTRIBUTE_NAME, value);
//				// Poți adăuga și alte atribute, cum ar fi 'class' sau 'type'
//				newAgentNode.addSingleValue("class", "com.example." + value);
//				currentTree.addOneTree(value, newAgentNode);
//				System.out.println("Agentul '" + value + "' a fost adaugat cu succes.");
//				return;
//			} else {
//				System.out.println("Calea specificata nu exista sau este invalida: " + command);
//				return;
//			}
//		}
//	}



}
