/**
 *  Copyright 2014 TangoMe Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.tango.elasticsearch.rest.action.unique.cache;

import java.util.Timer;
import java.util.TimerTask;

import net.sf.ehcache.CacheException;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;

import org.apache.log4j.Logger;

public class EhcacheWrapper<K, V> implements CacheWrapper<K, V> {

    public static final long CACHE_FLUSH_TASK_DELAY_MS = 10 * 60 * 1000;
    public static final long CACHE_FLUSH_TASK_PERIOD_MS = 10 * 60 * 1000;

    protected static final Logger LOG = Logger.getLogger(EhcacheWrapper.class);

    private final String cacheName;
    private final CacheManager cacheManager;

    public EhcacheWrapper(final String cacheName, final CacheManager cacheManager) {
        this.cacheName = cacheName;
        this.cacheManager = cacheManager;
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                cacheManager.shutdown();
            }
        });
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                cacheManager.getCache(cacheName).flush();
            }
        }, CACHE_FLUSH_TASK_DELAY_MS, CACHE_FLUSH_TASK_PERIOD_MS);
    }

    @Override
    public void put(final K key, final V value) {
        getCache().put(new Element(key, value));
    }

    @Override
    public V get(final K key) {
        Element element = getCache().get(key);
        if (element != null) {
            return (V) element.getObjectValue();
        }
        return null;
    }

    @Override
    public void clear() {
        try {
            getCache().removeAll();
        } catch (CacheException ex) {
            LOG.warn(String.format("Error clearing cache %s: %s", cacheName, ex.getMessage()), ex);
        }
    }

    public Ehcache getCache() {
        return cacheManager.getEhcache(cacheName);
    }

}
