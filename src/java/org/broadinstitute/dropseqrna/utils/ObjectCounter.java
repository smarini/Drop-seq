package org.broadinstitute.dropseqrna.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Keeps track of the number of times an object has been seen.
 * @author nemesh
 *
 * @param <T> The type of object to count.  Object needs to have an equals method implemented!
 */
public class ObjectCounter<T extends Comparable<T>> {

	private Map<T, Integer> countMap;
	
	public ObjectCounter () {
		countMap = new HashMap<T, Integer>();
	}
	
	public boolean hasKey (T object) {
		return this.countMap.containsKey(object);
	}
	
	public void increment (T object) {
		incrementByCount(object, 1);
	}
	
	public void clear() {
		this.countMap.clear();
	}
	
	public void incrementByCount (T object, int size) {
		Integer count = countMap.get(object);
		if (count==null) {
			countMap.put(object, size);
		} else {
			count+=size;
			countMap.put(object, count);
		}
	}
	
	public void setCount(T object, int count) {
		countMap.put(object, count);
	}
	
	public void remove (T object) {
		this.countMap.remove(object);
	}
	
	public Collection<T> getKeys () {
		return countMap.keySet();
	}
	
	public int getSize () {
		return countMap.size();
	}
	
	public int getCountForKey (T key) {
		Integer count = countMap.get(key);
		if (count==null) return 0;
		return count;
	}
	
	public int getTotalCount() {
		int result = 0;
		for (T key: this.countMap.keySet()) {
			int t = getCountForKey(key);
			result+=t;
		}
		return result;
	}
	
	public int getNumberOfSize(int size) {
		int result = 0;
		for (T key: this.countMap.keySet()) {
			int t = getCountForKey(key);
			if (t==size) result++;
		}
		return result;
	}
	
	public T getMode () {
		T max = null;
		int maxCount=0;
		for (T key: this.getKeys()) {
			int count=this.getCountForKey(key);
			if (count>maxCount) {
				max = key;
				maxCount=count;
			}
		}
		return (max);
	}
	// NOTE: 
	// for keys with the same number of items, object ordering is undefined.  Need to fix this to break ties by the T's natural ordering.	
	public List<T> getKeysOrderedByCount (boolean decreasing) {
		Map<Integer, List<T>> reversed= this.getReverseMapping();
		List<Integer> counts = new ArrayList<Integer>(reversed.keySet());
		Collections.sort(counts);
		if (decreasing) Collections.reverse(counts);
		List<T> keys = new ArrayList<T>();
		for (int i: counts) {
			List<T> t = reversed.get(i);
			Collections.sort(t);
			keys.addAll(t);
		}
		return (keys);
	}
	
	public Map<Integer, List<T>> getReverseMapping () {
		Map<Integer, List<T>> result = new HashMap<Integer, List<T>>(this.countMap.size());
		for (T key : this.countMap.keySet()) {
			Integer v =getCountForKey(key);
			List<T> l = result.get(v);
			if (l==null) {
				l=new ArrayList<T>();
			} 
			l.add(key);
			result.put(v, l);
		}
		return result;
	}
	
	/**
	 * Filters this counter to that only entries with at least <count> number of reads remain. 
	 */
	public void filterByMinCount (int count) {
		Map<T, Integer> result = new HashMap<T, Integer>();
		for (T key: this.countMap.keySet()) {
			Integer value = countMap.get(key);
			if (value>=count) {
				result.put(key, value);
			}
		}
		this.countMap=result;
	}
	
	public String toString () {
		return this.countMap.toString();
	}
}
