package com.taobao.datax.plugins.writer.extehwriter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.log4j.Logger;
import org.codehaus.jackson.map.ObjectMapper;
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexResponse;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsResponse;
import org.elasticsearch.action.admin.indices.exists.types.TypesExistsResponse;
import org.elasticsearch.action.admin.indices.mapping.put.PutMappingRequest;
import org.elasticsearch.action.admin.indices.mapping.put.PutMappingResponse;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.Requests;
import org.elasticsearch.common.settings.Settings;

public class ExtESHBaseProxy {

	private Configuration config;

	private Connection connection;

	private Table htable;

	private Table exthtable;

	private Admin admin;

	private HTableDescriptor descriptor;

	// 批量提交的记录数
	private int bulksize;
	// 索引名称
	private String indexname;
	// 类型名称(类似表名)
	private String typename;

	// 索引名称
	private String extindexname;
	// 类型名称(类似表名)
	private String exttypename;

	private Client client;

	private BulkRequestBuilder bulkRequestBuilder;
	
	private BulkRequestBuilder extbulkRequestBuilder;

	private static final int BUFFER_LINE = 1024;

	private List<Put> buffer = new ArrayList<Put>(BUFFER_LINE);
	
	private List<Put> extbuffer = new ArrayList<Put>(BUFFER_LINE);


	private Put p;
	private Put extp;
	
	private boolean rowExistAtHtable=false;
	
	private Logger logger = Logger.getLogger(ExtESHBaseProxy.class);

	ObjectMapper mapper = new ObjectMapper();

	public static ExtESHBaseProxy newProxy(String hbase_conf, String table, String exttable, Client client, int bulksize, String indexname, String extindexname, String typename, String exttypename)
			throws IOException {
		return new ExtESHBaseProxy(hbase_conf, table, exttable, client, bulksize, indexname, extindexname, typename, exttypename);
	}

	private ExtESHBaseProxy(String hbase_conf, String tableName, String exttable, Client client, int bulksize, String indexname, String extindexname, String typename, String exttypename)
			throws IOException {
		Configuration conf = new Configuration();
		conf.addResource(new Path(hbase_conf));
		config = new Configuration(conf);
		htable = getTable(tableName);
		exthtable = getTable(exttable);
		admin = getHBaseConnection().getAdmin();
		descriptor = htable.getTableDescriptor();
		
		this.client = client;
		this.bulksize = bulksize;
		this.indexname = indexname;
		this.typename = typename;
		this.extindexname = extindexname;
		this.exttypename = exttypename;
	}

	private Table getTable(String tableName) throws IOException {
		return getHBaseConnection().getTable(TableName.valueOf(tableName));
	}

	private Connection getHBaseConnection() {
		if (connection == null) {
			try {
				connection = ConnectionFactory.createConnection(config);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return connection;
	}

	public void setBufferSize(int bufferSize) throws IOException {
		this.htable.setWriteBufferSize(bufferSize);
	}

	public void setHTable(String tableName) throws IOException {
		this.htable = getTable(tableName);
	}

	public void setAutoFlush(boolean autoflush) {
		// this.htable.setAutoFlush(autoflush);
	}

	public boolean check() throws IOException {
		// if (!admin.isMasterRunning()) {
		// throw new IllegalStateException("hbase master is not running!");
		// }
		if (!admin.tableExists(htable.getName())) {
			throw new IllegalStateException("hbase table " + Bytes.toString(htable.getName().getName()) + " is not existed!");
		}
		if (!admin.isTableAvailable(htable.getName())) {
			throw new IllegalStateException("hbase table " + Bytes.toString(htable.getName().getName()) + " is not available!");
		}
		if (!admin.isTableEnabled(htable.getName())) {
			throw new IllegalStateException("hbase table " + Bytes.toString(htable.getName().getName()) + " is disable!");
		}

		return true;
	}

	public void close() throws IOException {
		htable.close();
		exthtable.close();
		if (this.bulkRequestBuilder != null) {
			this.bulkRequestBuilder = null;
		}
		if (this.extbulkRequestBuilder != null) {
			this.extbulkRequestBuilder = null;
		}
	}

	public void deleteESTable(String indexname, String typename) throws Exception {
//		DeleteMappingResponse response = client.admin().indices().prepareDeleteMapping(indexname).setType(typename).execute().actionGet();
//		DeleteMappingResponse response2 = client.admin().indices().prepareDeleteMapping(this.extindexname).setType(exttypename).execute().actionGet();
//		if (!(response.isAcknowledged() || response2.isAcknowledged())) {
//			throw new Exception(response.toString());
//		} else {
//			logger.debug("删除索引Type：" + indexname + "-" + typename + " 成功！");
//		}
	}

	public void createESTable(String indexname, int number_of_shards, int number_of_replicas, String typename, int bulksize, String mapping_xml) throws Exception {
		// 判断是否已经存在索引
		IndicesExistsResponse response = client.admin().indices().prepareExists(indexname).execute().actionGet();
		if (!response.isExists()) {
			// 建立索引
			CreateIndexResponse creaResp = client.admin().indices().prepareCreate(indexname)
					.setSettings(Settings.settingsBuilder().put("number_of_shards", number_of_shards).put("number_of_replicas", number_of_replicas)).execute().actionGet();
			if (!creaResp.isAcknowledged()) {
				throw new Exception(creaResp.toString());
			}
			// 建立type和mapping
			PutMappingRequest mapping = Requests.putMappingRequest(indexname).type(typename).source(mapping_xml);
			PutMappingResponse respMap = client.admin().indices().putMapping(mapping).actionGet();
			if (!respMap.isAcknowledged()) {
				throw new Exception(respMap.toString());
			} else {
				logger.debug("新建索引：" + indexname + "下的type:" + typename + " 成功！mapping格式为:" + mapping_xml);
			}
		} else {
			logger.debug("新建索引：" + indexname + " 成功！");
		}

		IndicesExistsResponse response2 = client.admin().indices().prepareExists(extindexname).execute().actionGet();
		if (!response2.isExists()) {
			// 建立索引
			CreateIndexResponse creaResp = client.admin().indices().prepareCreate(extindexname)
					.setSettings(Settings.settingsBuilder().put("number_of_shards", number_of_shards).put("number_of_replicas", number_of_replicas)).execute().actionGet();
			if (!creaResp.isAcknowledged()) {
				throw new Exception(creaResp.toString());
			}
			// 建立type和mapping
			PutMappingRequest mapping = Requests.putMappingRequest(extindexname).type(exttypename).source(mapping_xml);
			PutMappingResponse respMap = client.admin().indices().putMapping(mapping).actionGet();
			if (!respMap.isAcknowledged()) {
				throw new Exception(respMap.toString());
			} else {
				logger.debug("新建索引：" + extindexname + "下的type:" + exttypename + " 成功！mapping格式为:" + mapping_xml);
			}
		} else {
			logger.debug("新建索引：" + extindexname + " 成功！");
		}

		TypesExistsResponse typeResp = client.admin().indices().prepareTypesExists(indexname).setTypes(typename).execute().actionGet();
		if (!typeResp.isExists()) {
			// 建立type和mapping
			PutMappingRequest mapping = Requests.putMappingRequest(indexname).type(typename).source(mapping_xml);
			PutMappingResponse respMap = client.admin().indices().putMapping(mapping).actionGet();
			if (!respMap.isAcknowledged()) {
				throw new Exception(respMap.toString());
			} else {
				logger.debug("新建索引：" + indexname + "下的type:" + typename + " 成功！mapping格式为:" + mapping_xml);
			}
		}
		TypesExistsResponse typeResp2 = client.admin().indices().prepareTypesExists(extindexname).setTypes(exttypename).execute().actionGet();
		if (!typeResp2.isExists()) {
			// 建立type和mapping
			PutMappingRequest mapping = Requests.putMappingRequest(extindexname).type(exttypename).source(mapping_xml);
			PutMappingResponse respMap = client.admin().indices().putMapping(mapping).actionGet();
			if (!respMap.isAcknowledged()) {
				throw new Exception(respMap.toString());
			} else {
				logger.debug("新建索引：" + extindexname + "下的type:" + exttypename + " 成功！mapping格式为:" + mapping_xml);
			}
		}
	}

	public void truncateESTable(String indexname, int number_of_shards, int number_of_replicas, String typename, String mapping_xml) throws Exception {
//		IndicesExistsResponse exresponse = client.admin().indices().prepareExists(indexname).execute().actionGet();
//		if (exresponse.isExists()) {
//			try {
//				DeleteIndexResponse delresponse = client.admin().indices().prepareDelete(indexname).execute().actionGet();
//				if (!delresponse.isAcknowledged()) {
//					throw new Exception(delresponse.toString());
//				} else {
//					logger.debug("删除索引：" + indexname + " 成功！");
//				}
//			} catch (Exception ex) {
//			}
//		}
		IndicesExistsResponse exresponse2 = client.admin().indices().prepareExists(extindexname).execute().actionGet();
		if (exresponse2.isExists()) {
			try {
				DeleteIndexResponse delresponse = client.admin().indices().prepareDelete(extindexname).execute().actionGet();
				if (!delresponse.isAcknowledged()) {
					throw new Exception(delresponse.toString());
				} else {
					logger.debug("删除索引：" + extindexname + " 成功！");
				}
			} catch (Exception ex) {
			}
		}
		
//		CreateIndexResponse creaResp = client.admin().indices().prepareCreate(indexname)
//				.setSettings(ImmutableSettings.settingsBuilder().put("number_of_shards", number_of_shards).put("number_of_replicas", number_of_replicas)).execute().actionGet();
//		if (!creaResp.isAcknowledged()) {
//			throw new Exception(creaResp.toString());
//		}
//		PutMappingRequest mapping = Requests.putMappingRequest(indexname).type(typename).source(mapping_xml);
//		PutMappingResponse respMap = client.admin().indices().putMapping(mapping).actionGet();
//		if (!respMap.isAcknowledged()) {
//			throw new Exception(respMap.toString());
//		} else {
//			logger.debug("新建索引：" + indexname + "下的type:" + typename + " 成功！mapping格式为:" + mapping_xml);
//		}
		
		CreateIndexResponse creaResp2 = client.admin().indices().prepareCreate(extindexname)
				.setSettings(Settings.settingsBuilder().put("number_of_shards", number_of_shards).put("number_of_replicas", number_of_replicas)).execute().actionGet();
		if (!creaResp2.isAcknowledged()) {
			throw new Exception(creaResp2.toString());
		}
		PutMappingRequest mapping2 = Requests.putMappingRequest(extindexname).type(exttypename).source(mapping_xml);
		PutMappingResponse respMap2 = client.admin().indices().putMapping(mapping2).actionGet();
		if (!respMap2.isAcknowledged()) {
			throw new Exception(respMap2.toString());
		} else {
			logger.debug("新建索引：" + extindexname + "下的type:" + exttypename + " 成功！mapping格式为:" + mapping_xml);
		}
	}

	public void deleteTable() throws IOException {
		Scan s = new Scan();
		ResultScanner scanner = htable.getScanner(s);
		for (Result rr = scanner.next(); rr != null; rr = scanner.next()) {
			htable.delete(new Delete(rr.getRow()));
		}
		scanner.close();
	}

	public void truncateTable() throws IOException {
		admin.disableTable(htable.getName());
		admin.deleteTable(htable.getName());
		admin.createTable(descriptor);
	}

	public void flush() throws IOException {
		if (!buffer.isEmpty()) {
			htable.put(buffer);
			buffer.clear();
		}
		if (!extbuffer.isEmpty()) {
			exthtable.put(extbuffer);
			extbuffer.clear();
		}
		admin.flush(htable.getName());
		admin.flush(exthtable.getName());
		// elasticsearch 提交
		BulkResponse response = bulkRequestBuilder.execute().actionGet();
		logger.debug(getFailueMessage(response));
		bulkRequestBuilder = null;
		BulkResponse response2 = extbulkRequestBuilder.execute().actionGet();
		logger.debug(getFailueMessage(response2));
		extbulkRequestBuilder = null;
		// htable.flushCommits();
	}

	private String getFailueMessage(BulkResponse response) {
		StringBuilder sb = new StringBuilder();
		boolean havFailure = false;
		for (BulkItemResponse item : response.getItems()) {
			if (item.isFailed()) {
				havFailure = true;
				sb.append("\n[").append("]: index [").append(item.getIndex()).append("], type [").append(item.getType()).append("], id [").append(item.getId()).append("], message [")
						.append(item.getFailureMessage()).append("]");
			}
		}
		if (havFailure) {
			return "failure in bulk execution:" + sb.toString();
		} else {
			return "";
		}
	}

	public void prepare(byte[] rowKey) {
		//判断原表是否存在该rowKey值；
		Get get=new Get(rowKey);
		Result ru;
		try {
			ru = htable.get(get);
			if (ru==null || ru.isEmpty()){
				rowExistAtHtable=false;
				this.extp=new Put(rowKey);
			}else{
				rowExistAtHtable=true;
				this.p = new Put(rowKey);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void prepareEs() {
		this.bulkRequestBuilder = client.prepareBulk();
		this.extbulkRequestBuilder=client.prepareBulk();
	}

	public Put add(byte[] family, byte[] qualifier, long timeStamp, byte[] value) {
		if (rowExistAtHtable){
			return this.p.addColumn(family, qualifier, timeStamp, value);
		}else{
			return this.extp.addColumn(family, qualifier, timeStamp, value);
		}
	}

	public Put add(byte[] family, byte[] qualifier, byte[] value) {
		if (rowExistAtHtable){
			return this.p.addColumn(family, qualifier, value);
		}else{
			return this.extp.addColumn(family, qualifier, value);
		}
	}

	public void insert(String id, Map map) throws IOException {
		if (rowExistAtHtable){
			buffer.add(this.p);
			if (buffer.size() >= BUFFER_LINE) {
				htable.put(buffer);
				buffer.clear();
			}
			try {
				if (StringUtils.isEmpty(id)) {
					bulkRequestBuilder.add(client.prepareIndex(this.indexname, this.typename).setSource(map).request());
				} else {
					bulkRequestBuilder.add(client.prepareIndex(this.indexname, this.typename, id).setSource(map).request());
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			if (bulkRequestBuilder.numberOfActions() >= this.bulksize) {
				BulkResponse response = bulkRequestBuilder.execute().actionGet();
				String err = getFailueMessage(response);
				if (err.length() > 0) {
					logger.info(err);
				}
				this.bulkRequestBuilder = client.prepareBulk();
			}
		}else{
			extbuffer.add(this.extp);
			if (extbuffer.size() >= BUFFER_LINE) {
				exthtable.put(extbuffer);
				extbuffer.clear();
			}
			try {
				if (StringUtils.isEmpty(id)) {
					extbulkRequestBuilder.add(client.prepareIndex(this.extindexname, this.exttypename).setSource(map).request());
				} else {
					extbulkRequestBuilder.add(client.prepareIndex(this.extindexname, this.exttypename, id).setSource(map).request());
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			if (extbulkRequestBuilder.numberOfActions() >= this.bulksize) {
				BulkResponse response = extbulkRequestBuilder.execute().actionGet();
				String err = getFailueMessage(response);
				if (err.length() > 0) {
					logger.info(err);
				}
				this.extbulkRequestBuilder=client.prepareBulk();
			}
		}
		
	}
}
