package com.singularity.ee.agent.systemagent.monitors.common;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.singularity.ee.agent.systemagent.api.MetricWriter;
import com.singularity.ee.agent.systemagent.api.TaskExecutionContext;
import com.singularity.ee.agent.systemagent.api.TaskOutput;
import com.singularity.ee.agent.systemagent.api.exception.TaskExecutionException;
import com.singularity.ee.agent.systemagent.monitors.common.JavaServersMonitor;
import com.singularity.ee.util.clock.ClockUtils;
import com.tibco.tibjms.admin.ConnectionInfo;
import com.tibco.tibjms.admin.QueueInfo;
import com.tibco.tibjms.admin.ServerInfo;
import com.tibco.tibjms.admin.TibjmsAdmin;
import com.tibco.tibjms.admin.TibjmsAdminException;

public class TibcoEMSMonitor3 extends JavaServersMonitor {
	private volatile String tierName;
	private volatile String serverName;
	private volatile int refreshIntervalInExecutionTicks;
	private final Map<String, String> cachedValueMap;
	private volatile int currentNumExecutionTicks = -1;
	private volatile List<String> columnNames;

	private volatile String userid;
	private volatile String password;
	private volatile String hostname;
	private volatile String port;

	public TibcoEMSMonitor3() {
		oldValueMap = Collections
				.synchronizedMap(new HashMap<String, String>());
		cachedValueMap = Collections
				.synchronizedMap(new HashMap<String, String>());
	}

	protected void parseArgs(Map<String, String> args) {
		super.parseArgs(args);
		tierName = getArg(args, "tier", null); // if the tier is not specified
												// then create the metrics for
												// all tiers
		userid = getArg(args, "userid", null);
		password = getArg(args, "password", null);
		hostname = getArg(args, "hostname", null);
		port = getArg(args, "port", "7222");
		serverName = getArg(args, "emsservername", null);

		// Assume all the columns we want values for are in a comma separated
		// list
		String columnNamesString = getArg(args, "columns", null);

		if (columnNamesString == null || columnNamesString.length() == 0) {
			columnNames = Collections.emptyList();
		} else {
			columnNames = Arrays.asList(columnNamesString.split(","));
		}

		int refreshIntervalSecs = Integer.parseInt(getArg(args,	"refresh-interval", "60"));

		if (refreshIntervalSecs <= 60) {
			refreshIntervalInExecutionTicks = 1;
		} else {
			// Convert refresh interval to milliseconds and round up to the
			// nearest minute timeslice.
			// From that we can get the number of 60 second ticks before the
			// next refresh.
			// We do this to prevent time drift issues from preventing this task
			// from running.
			refreshIntervalInExecutionTicks = (int) (ClockUtils
					.roundUpTimestampToNextMinute(refreshIntervalSecs * 1000) / 60000);
		}

		if (currentNumExecutionTicks == -1) {
			// This is the first time we've parsed the args. Assume we refresh
			// the data
			// the next time we execute the monitor.
			currentNumExecutionTicks = refreshIntervalInExecutionTicks;
		}
	}

	private TibjmsAdmin connect() throws TibjmsAdminException {

		TibjmsAdmin tibcoAdmin = new TibjmsAdmin("tcp://" + hostname + ":"
				+ port, userid, password);
		
		return tibcoAdmin;
	}

	// collects all monitoring data for this time period from database
	private Map<String, String> putValuesIntoMap() throws Exception {
		Map<String, String> columnName2Value = new HashMap<String, String>();

		TibjmsAdmin conn = null;
		boolean debug = logger.isDebugEnabled();
		try {
			conn = connect();
			if (debug) {
				logger.debug("Connecting to " + conn.getInfo());
			}
			ServerInfo serverInfo = conn.getInfo();
			serverInfo.getAsyncDBSize();
			serverInfo.getDetailedStatistics();
			serverInfo.getConsumerCount();
			serverInfo.getDiskReadRate();
			serverInfo.getDiskWriteRate();
			serverInfo.getInboundBytesRate();

			serverInfo.getInboundMessageRate();
			serverInfo.getOutboundBytesRate();
			serverInfo.getOutboundMessageCount();
			serverInfo.getOutboundMessageRate();
			serverInfo.getMaxClientMsgSize();
			serverInfo.getMaxConnections();
			serverInfo.getMaxMsgMemory();
			serverInfo.getMaxStatisticsMemory();
			serverInfo.getProducerCount();
			serverInfo.getConsumerCount();
			
			columnName2Value.put( "ConnectionCount",new Integer(serverInfo.getConnectionCount()).toString());
			columnName2Value.put( "PendingMessageCount",new Long(serverInfo.getPendingMessageCount()).toString());
			columnName2Value.put( "PendingMessageSize",new Long(serverInfo.getPendingMessageSize()).toString());
			columnName2Value.put( "InboundMessageCount",new Long(serverInfo.getInboundMessageCount()).toString());
			columnName2Value.put( "OutboundMessageCount",new Long(serverInfo.getOutboundMessageCount()).toString());
			
			
			// get most accurate time
			currentTime = System.currentTimeMillis();
			logger.debug("Retrieving Queue Information");
			QueueInfo[] queueInformation = conn.getQueuesStatistics();
			for (int i = 0; i < queueInformation.length; i++) {
				QueueInfo queueInfo = queueInformation[i];

				
				columnName2Value.put( queueInfo.getName() + "|ConsumerCount".toUpperCase(),		
												new Integer(queueInfo.getConsumerCount()).toString());
				columnName2Value.put(queueInfo.getName() + "|DeliveredMessageCount".toUpperCase(), 
												new Long(queueInfo.getDeliveredMessageCount()).toString());
				columnName2Value.put(queueInfo.getName() + "|ConsumerCount".toUpperCase(), 
												new Long(queueInfo.getFlowControlMaxBytes()).toString());
				columnName2Value.put(queueInfo.getName() + "|PendingMessageCount".toUpperCase(), 
												new Long(queueInfo.getPendingMessageCount()).toString());
				columnName2Value.put(queueInfo.getName() + "|FlowControlMaxBytes".toUpperCase(), 
												new Long(queueInfo.getFlowControlMaxBytes()).toString());
				columnName2Value.put(queueInfo.getName() + "|MaxMsgs".toUpperCase(), 
												new Long(queueInfo.getMaxMsgs()).toString());
				columnName2Value.put(queueInfo.getName() + "|PendingMessageSize".toUpperCase(), 
												new Long(queueInfo.getPendingMessageSize()).toString());
				columnName2Value.put(queueInfo.getName() + "|ReceiverCount".toUpperCase(), 
												new Long(queueInfo.getReceiverCount()).toString());
				columnName2Value.put(queueInfo.getName() + "|MaxMsgs".toUpperCase(), 
												new Long(queueInfo.getMaxMsgs()).toString());
				columnName2Value.put(queueInfo.getName() + "|MaxBytes".toUpperCase(), 
												new Long(queueInfo.getMaxBytes()).toString());
				
				
				// Compare Pending Message Size against the Max Message Size. 
				// 

				
				logger.debug(queueInfo.getName());
				logger.debug("Consumer Count "			+ queueInfo.getConsumerCount());
				logger.debug("Delivered Message Count " + queueInfo.getDeliveredMessageCount());
				logger.debug("Flow Control Max Bytes "	+ queueInfo.getFlowControlMaxBytes());
				logger.debug("Pending Message Count "	+ queueInfo.getPendingMessageCount());
				logger.debug("FlowControlMaxBytes" + queueInfo.getFlowControlMaxBytes());
				logger.debug("MaxMessages " + queueInfo.getMaxMsgs());
				logger.debug("ReceiverCount " + queueInfo.getReceiverCount());
				
			}
			System.out.println("Closing Connection to Server");
			conn.close();

		} 
		catch (com.tibco.tibjms.admin.TibjmsAdminException ex) {
		        logger.error("Error connecting to EMS Server" + serverName + " " 
		        		+ port + " " + this.hostname + " " + this.password, ex);
		
	    }
		catch (Exception ex) {
			logger.error("Error getting performance data from Tibco EMS.", ex);
			throw ex;
		
	    }
		finally {
		   conn.close();
		}
		return Collections.synchronizedMap(columnName2Value);
	}

	public TaskOutput execute(Map<String, String> taskArguments,
			TaskExecutionContext taskContext) throws TaskExecutionException {
		
		startExecute(taskArguments, taskContext);

		// just for debug output
		logger.debug("Starting METRIC COLLECTION for Tibco EMS  Monitor.......");

		Map<String, String> map;
		try {
			map = this.putValuesIntoMap();
			Iterator keys = map.keySet().iterator();
			while (keys.hasNext()) {
				String key = (String) keys.next();
				String value = map.get(key);
				printMetric(key, value);
				if (logger.isDebugEnabled()) {
					logger.debug("Key :" + key + " : " + value);
				}

			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return this.finishExecute();
	}

	private void printMetric(String name, String value) {
		printMetric(name, value,
				MetricWriter.METRIC_AGGREGATION_TYPE_OBSERVATION,
				MetricWriter.METRIC_TIME_ROLLUP_TYPE_CURRENT,
				MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE);

	}

	protected String getMetricPrefix() {
		if (tierName != null) {
			return "Server|Component:" + tierName + "|" + serverName +"|";
		} else {
			return "Custom Metrics|Tibco EMS Server|";
		}
	}

}
















