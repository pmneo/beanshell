
/** Copyright 2018 Nick nickl- Lombard
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. */
package bsh.util;

import java.lang.ref.WeakReference;
import java.util.ConcurrentModificationException;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;

/** Asynchronous reference cache with weak, soft and hard reference support.
 * Implementations supply values via the abstract create method, which is
 * called from a future asynchronously. Garbage collected references are
 * monitored and removed from the cache once cleared.
 * @param <K> the type of keys maintained by this cache
 * @param <V> the type of cached values */
public class ReferenceCache<K,V> {
    private final WeakHashMap<K,WeakReference<V>> cache = new WeakHashMap<>();
    private final Function<K,V> valueFactory;

    /** New cache with initial size of key type and value type references.
     * @param keyType the type of key reference
     * @param valueType the type of value reference
     * @param initialSize initial cache size */
    public ReferenceCache( Function<K,V> valueFactory ) {
        this.valueFactory = valueFactory;
    }

    /** Get a value from the cache for associated with the supplied key.
     * New entries will be initialized if they don't exist or if they were
     * cleared and will block to wait for a value to return.
     * For asynchronous non blocking value creation use init.
     * @param key associated with cache value
     * @return value associated with the key */
    public V get(K key) {
        if( key == null ) {
            return null;
        }

        synchronized( cache ) {
            int retry = 0;
            while( true ) {
                try {
                    WeakReference<V> ref = cache.get( key );
                    if( ref == null || ref.get() == null ) {
                        cache.put( key, ref = new WeakReference<>( valueFactory.apply( key ) ) );
                    }

                    return ref.get();
                }
                catch( ConcurrentModificationException cme ) {
                    //SILENT_CATCH
                    retry++;
                    if( retry > 100 ) {
                        throw cme;
                    }
                }
            }
        }
    }

    public void init (K key) {
        get( key );
    }


    /** Remove cache entry associated with the given key.
     * @param key associated with cache value
     * @return true if there was an entry to remove */
    public boolean remove(K key) {
        if (null == key)
            return false;
        synchronized( cache ) {
            int retry = 0;
            while( true ) {
                try {
                    return cache.remove( key ) != null;
                }
                catch( ConcurrentModificationException cme ) {
                    //SILENT_CATCH
                    retry++;
                    if( retry > 100 ) {
                        throw cme;
                    }
                }
            }
        }
    }

    /** Returns the number of cached entries in the cache.
     * @return the number of entries cached */
    public int size() { return cache.size(); }


    /** Clears the cache and removes all of the cached entries.
     * The cache will be empty after this call returns. */
    public void clear() { cache.clear(); }


    @Override
    public String toString() {
        synchronized( cache ) {
            int empty = 0;
            int nonEmpty = 0;
            for( Map.Entry<K,WeakReference<V>> e : cache.entrySet() ) {
                if( e.getValue().get() == null ) empty ++;
                else nonEmpty ++;
            }
            return nonEmpty + "/" + empty + " of " + cache.size();
        }
    }
}
