package br.com.caelum.vraptor.dash.hibernate;

import java.io.IOException;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.sf.ehcache.CacheManager;
import net.vidageek.mirror.dsl.Mirror;

import org.hibernate.Session;
import org.hibernate.stat.EntityStatistics;
import org.hibernate.stat.QueryStatistics;
import org.hibernate.stat.Statistics;

import br.com.caelum.vraptor.Path;
import br.com.caelum.vraptor.Resource;
import br.com.caelum.vraptor.Result;
import br.com.caelum.vraptor.dash.hibernate.stats.CacheStatsWrapper;
import br.com.caelum.vraptor.dash.hibernate.stats.CollectionStatsWrapper;
import br.com.caelum.vraptor.dash.hibernate.stats.EntityCacheStatsWrapper;
import br.com.caelum.vraptor.dash.hibernate.stats.EntityStatsWrapper;
import br.com.caelum.vraptor.dash.hibernate.stats.QueryStatsWrapper;

import com.mchange.v2.c3p0.mbean.C3P0PooledDataSource;

@Resource
public class AuditController {

	private final Session session;
	private final Result result;

	public AuditController(Session session, Result result) {
		this.session = session;
		this.result = result;
	}

	@Path("/auditoria/estatisticas")
	public void listStats() throws IOException {
		session.getSessionFactory().getStatistics();

		result.forwardTo("");
	}
	
	@Path("/auditoria/painelDeControle")
	public void controlPanel() {
		NumberFormat decimalFormat = NumberFormat.getNumberInstance();
		decimalFormat.setGroupingUsed(true);

		Statistics statistics = session.getSessionFactory().getStatistics();
		
		result.include("connectionCount", decimalFormat.format(statistics.getConnectCount()));
		
		result.include("secondLevelCacheMissCount", decimalFormat.format(statistics.getSecondLevelCacheMissCount()));
		result.include("secondLevelCacheHitCount", decimalFormat.format(statistics.getSecondLevelCacheHitCount()));
		result.include("secondLevelCachePutCount", decimalFormat.format(statistics.getSecondLevelCachePutCount()));
		
		Runtime runtime = Runtime.getRuntime();
		
		
		NumberFormat percentFormat = NumberFormat.getPercentInstance();
		
		result.include("totalMemory", decimalFormat.format(runtime.totalMemory()));
		
		double usedMemory = runtime.totalMemory() - runtime.freeMemory();
		result.include("usedMemory", decimalFormat.format(usedMemory));
		result.include("usedMemoryPerCent", percentFormat.format(usedMemory / runtime.totalMemory()));
		

		result.include("freeMemory", decimalFormat.format(runtime.freeMemory()));
		double freeMemory = runtime.freeMemory();
		result.include("freeMemoryPerCent", percentFormat.format(freeMemory / runtime.totalMemory()));
		
		C3P0PooledDataSource c3p0PooledDataSource = new C3P0PooledDataSource();
		result.include("maxPoolSize", c3p0PooledDataSource.getMaxPoolSize());
		result.include("initPoolSize", c3p0PooledDataSource.getInitialPoolSize());
		result.include("minPoolSize", c3p0PooledDataSource.getMinPoolSize());
		
		String[] queries = statistics.getQueries();
		List<QueryStatsWrapper> queryStatsList = new ArrayList<QueryStatsWrapper>();
		for (String query : queries) {
			QueryStatistics queryStats = statistics.getQueryStatistics(query);
			queryStatsList.add(new QueryStatsWrapper(query, queryStats));
		}
		result.include("queryStatsList", queryStatsList);
		
		String[] entityNames = statistics.getEntityNames();
		Map<String, EntityCacheStatsWrapper> entityCacheStats = new HashMap<String, EntityCacheStatsWrapper>();
		
		for (String entityName : entityNames) {
			EntityStatistics entityStatistics = statistics.getEntityStatistics(entityName);
			EntityCacheStatsWrapper entityCacheStatsWrapper = new EntityCacheStatsWrapper();
			entityCacheStatsWrapper.setEntityStatsWrapper(new EntityStatsWrapper(entityName, entityStatistics));
			entityCacheStats.put(entityName, entityCacheStatsWrapper);
		}
		
		for (String regionName : statistics.getSecondLevelCacheRegionNames()) {
			CacheStatsWrapper cacheStatsWrapper = new CacheStatsWrapper(regionName, statistics.getSecondLevelCacheStatistics(regionName));
			if(entityCacheStats.containsKey(regionName)){
				EntityCacheStatsWrapper entityCacheStatsWrapper = entityCacheStats.get(regionName);
				EntityCacheStatsWrapper entityStatsWrapper = entityCacheStatsWrapper;
				entityStatsWrapper.setCacheStatsWrapper(cacheStatsWrapper);
			}
			else {
				EntityCacheStatsWrapper entityCacheStatsWrapper = new EntityCacheStatsWrapper();
				entityCacheStatsWrapper.setCacheStatsWrapper(cacheStatsWrapper);
				entityCacheStats.put(regionName, entityCacheStatsWrapper);
			}
		}
		
		result.include("entityCacheStats", entityCacheStats);
		
		
		List<CollectionStatsWrapper> collectionsStatsList = new ArrayList<CollectionStatsWrapper>();
		for (String collectionRoleName : statistics.getCollectionRoleNames()) {
			collectionsStatsList.add(new CollectionStatsWrapper(collectionRoleName, statistics));
		}
		result.include("collectionsStatsList", collectionsStatsList);
		
		List<net.sf.ehcache.Statistics> collectionsCacheStatsList = new ArrayList<net.sf.ehcache.Statistics>();
		List<CacheManager> allCacheManagers = CacheManager.ALL_CACHE_MANAGERS;
		for (CacheManager cacheManager : allCacheManagers) {
			for (String cacheName : cacheManager.getCacheNames()) {
				collectionsCacheStatsList.add(cacheManager.getCache(cacheName).getStatistics());
			}
		}
		result.include("ehCacheStatsList", collectionsCacheStatsList);
		
		includeMethodInvocationReturnInResult("numBusyCon", c3p0PooledDataSource, "getNumBusyConnectionsAllUsers");
		includeMethodInvocationReturnInResult("numCon", c3p0PooledDataSource, "getNumConnectionsAllUsers");
		includeMethodInvocationReturnInResult("numIdleCon", c3p0PooledDataSource, "getNumIdleConnectionsAllUsers");
		includeMethodInvocationReturnInResult("numUserPools", c3p0PooledDataSource, "getNumUserPools");
	}
	
	void includeMethodInvocationReturnInResult(String name, Object obj, String methodName) {
		try {
			Object toBeIncluded = new Mirror().on(obj).invoke().method(methodName).withoutArgs();
			result.include(name, toBeIncluded);
		} catch (Exception e) {
			result.include(name, "not found");
		}
	}
}