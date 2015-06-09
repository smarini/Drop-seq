package org.broadinstitute.dropseqrna.utils.editdistance;

import htsjdk.samtools.util.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.broadinstitute.dropseqrna.utils.ObjectCounter;

/**
 * A utility class that takes a list of strings (ordered by prevalence to determine which barcodes are merged into which)
 * and generates a map, containing the non merged barcodes as keys and the values of each key are a list of barcodes that are merged into a key.
 * @author nemesh
 *
 */
public class MapBarcodesByEditDistance {

	private CollapseBarcodeThreaded cbt=null;
	private final int NUM_THREADS;
	private final Log log = Log.getInstance(MapBarcodesByEditDistance.class);
	private final int REPORT_PROGRESS_INTERVAL;
	private int threadedBlockSize=20000;
	private final boolean verbose;
	
	
	public MapBarcodesByEditDistance (boolean verbose, int numThreads, int reportProgressInterval) {
		this.verbose=verbose;
		this.NUM_THREADS=numThreads;
		this.REPORT_PROGRESS_INTERVAL=reportProgressInterval;
		if (this.NUM_THREADS>1) cbt= new CollapseBarcodeThreaded(this.threadedBlockSize, this.NUM_THREADS);
	}
	
	public MapBarcodesByEditDistance (boolean verbose, int reportProgressInterval) {
		this(verbose, 1, reportProgressInterval);
	}
	
	public MapBarcodesByEditDistance (boolean verbose) {
		this(verbose, 1, 100000);
	}
	
	/**
	 * Perform edit distance collapse where all barcodes are eligible to be collapse.
	 * Barcodes are ordered by total number of counts.
	 * @param barcodes
	 * @param findIndels
	 * @param editDistance
	 * @return
	 */
	public Map<String, List<String>> collapseBarcodes (ObjectCounter<String> barcodes, boolean findIndels, int editDistance) {
		List<String> coreBarcodes = barcodes.getKeysOrderedByCount(true);
		return (collapseBarcodes(coreBarcodes, barcodes, findIndels, editDistance));
	}
	
	/**
	 * Collapses a list of barcodes.
	 * This works by iterating through every core barcode (ordered from largest to smallest in the barcodes object), and mapping any other barcode within editDistance
	 * to this barcode.  This means that the number of coreBarcodes at the end is less than or equal to the number of core barcodes submitted.  These barcodes are the keys of the output object.
	 * The barcodes that are not in the coreBarcodes list are eligible to be collapsed into a core barcode, but will never absorb other barcodes.
	 * We use coreBarcodes in order to limit the scope of the computational work, as the number of coreBarcodes can be small (the number of cells) 
	 * compared to the total number of barcodes (number of beads + bead sequencing errors.)
	 * Smaller barcodes are always collapsed into larger ones.  Each barcode only exists once in the output - if a barcode A is edit distance 1 away
	 * from barcode B and C, it will be assigned to whichever of B and C is larger.
	 * @param coreBarcodes A list of barcode strings that are considered "core" or primary barcodes.
	 * @param barcodes An exhaustive list of all barcodes (both core and non-core) with assigned counts of observations of these barcodes.
	 * @param findIndels If true, we use Levenshtein indel sensitive collapse.  If false, use Hamming distance.
	 * @param editDistance The maximum edit distance two barcodes can be at to be collapsed.
	 * @return
	 */
	public Map<String, List<String>> collapseBarcodes(List<String> coreBarcodes, ObjectCounter<String> barcodes, boolean findIndels, int editDistance) {
		// don't allow side effects to modify input lists.
		coreBarcodes = new ArrayList<String>(coreBarcodes);
		barcodes = new ObjectCounter<String>(barcodes);
		
		Map<String, List<String>> result = new HashMap<String, List<String>>();
		int count = 0;
		int numBCCollapsed=0;
		
		List<String> barcodeList = barcodes.getKeysOrderedByCount(true);
		//List<BarcodeWithCount> barcodesWithCount=getBarcodesWithCounts(barcodes);
		//List<String> barcodeList = EDUtils.getInstance().getBarcodes(barcodesWithCount);
		
		//int totalCount = barcodes.getTotalCount();
		
		int coreBarcodeCount=coreBarcodes.size();
		long startTime = System.currentTimeMillis();
		while (coreBarcodes.isEmpty()==false) {
			String b = coreBarcodes.get(0);
			count++;
			coreBarcodes.remove(b);
			barcodeList.remove(b);
			
			Set<String> closeBC=processSingleBarcode(b, barcodeList, findIndels, editDistance);
			numBCCollapsed+=closeBC.size();
			
			
			if (result.containsKey(b)) {
				log.error("Result should never have core barcode");
			}
			
			List<String> closeBCList = new ArrayList<String>(closeBC);
			Collections.sort(closeBCList);
			result.put(b, closeBCList);
			
			barcodeList.removeAll(closeBC);
			coreBarcodes.removeAll(closeBC);
			if (this.REPORT_PROGRESS_INTERVAL!=0 && count % this.REPORT_PROGRESS_INTERVAL == 0) {
				if (barcodes.getSize()>10000) log.info("Processed [" + count + "] records, totals BC Space left [" + barcodeList.size() +"]", " # collapsed this set [" + numBCCollapsed+"]");
				numBCCollapsed=0;
			}
		}
		if (verbose) {
			long endTime = System.currentTimeMillis();
			long duration = (endTime - startTime)/1000;
			log.info("Collapse with [" + this.NUM_THREADS +"] threads took [" + duration + "] seconds to process");
			log.info("Started with core barcodes [" +coreBarcodeCount+  "] ended with [" + count + "] num collapsed [" +  (coreBarcodeCount-count) +"]");
		}		
		return (result);
	}
	
	
	
	
	
	
	
	private Set<String> processSingleBarcode(String barcode, List<String> comparisonBarcodes, boolean findIndels, int editDistance) {
		Set<String> closeBarcodes =null;
		if (this.NUM_THREADS>1 ) {
			closeBarcodes=cbt.getStringsWithinEditDistanceWithIndel(barcode, comparisonBarcodes, editDistance, findIndels);
		} else {
			// single threaded mode for now.  Maybe remove this later?  Not sure if single threaded is slower, probably is.
			// would need to implement the multi-threaded indel sensitive barcode collapse.
			if (findIndels) {
				closeBarcodes = EDUtils.getInstance().getStringsWithinEditDistanceWithIndel(barcode,comparisonBarcodes, editDistance);
			} else {
				closeBarcodes = EDUtils.getInstance().getStringsWithinEditDistance(barcode,comparisonBarcodes, editDistance);
			}	
		}	
		return (closeBarcodes);
	}
	
	private List<BarcodeWithCount> getBarcodesWithCounts (ObjectCounter<String> barcodes) {
		List<BarcodeWithCount> result = new ArrayList<BarcodeWithCount>();
		List<String> keys = barcodes.getKeysOrderedByCount(true);
		for (String k: keys) {
			BarcodeWithCount b = new BarcodeWithCount(k, barcodes.getCountForKey(k));
			result.add(b);
		}
		return (result);
	}
}