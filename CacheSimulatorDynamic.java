/* 
 * Simulates a dynamic cache on the specified trace the specified allocation file
 */

import java.util.*;
import java.io.*;
import java.nio.file.*;

public class CacheSimulatorDynamic {
    
    public static int[] SLAB_SIZES = {64, 128, 256, 512, 1024, 2048, 4096, 8192, 
        16384, 32768, 65536, 131072, 262144, 524288, 1048576};
    public static int SLAB_SIZE = 1048576;
    public static int LINES_READ_PER_CHUNK = 5400;
    public static String traceLink = "./traces/hit-count-test-trace.txt";
    public static String allocationLink = "./results/hit-count-test-alloc.txt";
    

    public int hits;
    public int requests;
    public HashMap<Integer, Integer> slabAllocation;
    public BufferedReader slabAllocReader;

    public HashMap<Integer, Integer> cacheUsedSpace;
    public HashMap<Integer, Integer> cacheContents; // maps id to size
    public HashMap<Integer, LinkedHashSet<Integer>> cacheLRU;

    /* 
        Constructor
    */
    public CacheSimulatorDynamic() {
        this.hits = 0;
        this.requests = 1;
        this.slabAllocation = new HashMap<Integer, Integer>();

        this.cacheUsedSpace = new HashMap<Integer, Integer>();
        this.cacheContents = new HashMap<Integer, Integer>();
        this.cacheLRU = new HashMap<Integer, LinkedHashSet<Integer>>();
        
        for (int sc : SLAB_SIZES) {
            this.cacheUsedSpace.put(sc, 0);
            this.cacheLRU.put(sc, new LinkedHashSet<Integer>());
        }
    }

    public void initCache() {
        try {
            // determine the slab allocation for the trace by reading result file
            this.slabAllocReader = Files.newBufferedReader(Paths.get(allocationLink));
            this.slabAllocation = this.getNextSlabAllocation();

            // in allocator, we read LINES_READ_PER_CHUNK lines first, then calculate allocation
            // to line up with this, just use the first allocation twice 
            // this.slabAllocReader = Files.newBufferedReader(Paths.get(allocationLink));     

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public HashMap<Integer, Integer> getNextSlabAllocation() {
        try {
            HashMap<Integer, Integer> newSlabAlloc = new HashMap<Integer, Integer>();

            String line = this.slabAllocReader.readLine();
            line = line.substring(1, line.length() - 1);
            String[] keyVals = line.split(", ");
            for(String keyVal : keyVals ){
                String[] parts = keyVal.split("=", 2);
                int sc = Integer.parseInt(parts[0]);
                int slabs = Integer.parseInt(parts[1]);

                newSlabAlloc.put(sc, slabs);
            }   


            return newSlabAlloc;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    public BufferedReader openTrace() {
        // URL url = new URL(this.trace_url);
        // return new BufferedReader(new InputStreamReader(url.openStream()));
        try {
            return Files.newBufferedReader(Paths.get(traceLink));
        } catch (Exception e) {
            return null;
        }
    }

    public static int getSlabClass(int size) {
        for (int sc : SLAB_SIZES) {
            if (size < sc) {
                return sc;
            }
        }
        return SLAB_SIZES[SLAB_SIZES.length - 1];   
    }

    public void reallocSlabs(HashMap<Integer, Integer> newSlabAlloc) {
        for (int sc : this.slabAllocation.keySet()) {
            if (this.slabAllocation.get(sc) != newSlabAlloc.get(sc)) {
                // if the allocation has changed, evict the LRU items until desired allocation reached
                Iterator<Integer> currIt = this.cacheLRU.get(sc).iterator();
                while ((long) this.cacheUsedSpace.get(sc) > (long) newSlabAlloc.get(sc) * SLAB_SIZE) {
                    Integer removeItemId = currIt.next();
                    Integer removeItemSize = this.cacheContents.get(removeItemId);

                    this.cacheUsedSpace.put(sc, this.cacheUsedSpace.get(sc) - removeItemSize);
                    this.cacheContents.remove(removeItemId);   
                    currIt.remove();
                }
            }
        }
        this.slabAllocation = newSlabAlloc;
    }

    public void addToSlabClass(int sc, int id, int size) {
        // if there is just not enough space to hold the item, just don't store it
        if (size > this.slabAllocation.get(sc) * SLAB_SIZE) {
            return;
        }

        Iterator<Integer> currIt = this.cacheLRU.get(sc).iterator();

        // remove from the slab class until there is enough space
        while (this.slabAllocation.get(sc) * SLAB_SIZE < this.cacheUsedSpace.get(sc) + size) {
            Integer removeItemId = currIt.next();
            Integer removeItemSize = this.cacheContents.get(removeItemId);

            this.cacheUsedSpace.put(sc, this.cacheUsedSpace.get(sc) - removeItemSize);
            this.cacheContents.remove(removeItemId);   
            currIt.remove();
        }

        // add the item to the slab class
        this.cacheUsedSpace.put(sc, this.cacheUsedSpace.get(sc) + size);
        this.cacheContents.put(id, size);
        this.cacheLRU.get(sc).add(id);
    }

    public void processLine(String line) {
        // parse the line for id and size
        String[] itemArr = line.split(",");
        int id = Integer.parseInt(itemArr[1]);
        int size = Integer.parseInt(itemArr[2]) + Integer.parseInt(itemArr[3]);
        int sc = getSlabClass(size);

        // re-evaluate slab allocation and re-allocate if necessary
        if (this.requests % LINES_READ_PER_CHUNK == 0) {    
            HashMap<Integer, Integer> newSlabAlloc = this.getNextSlabAllocation();
            if (!newSlabAlloc.equals(this.slabAllocation)) {
                this.reallocSlabs(newSlabAlloc);
            }
        }

        if (this.cacheContents.containsKey(id)) {
            int oldSize = this.cacheContents.get(id);
            if (size != oldSize) {
                // if the size of the item has changed, then remove it from the old and add again
                int oldSC = getSlabClass(oldSize);
                this.cacheUsedSpace.put(oldSC, this.cacheUsedSpace.get(oldSC) - oldSize);   
                this.cacheContents.remove(id);
                this.cacheLRU.get(oldSC).remove(id);

                this.addToSlabClass(sc, id, size);
            } else {
                // if it's a hit, just move item up in LRU
                this.cacheLRU.get(sc).remove(id);
                this.cacheLRU.get(sc).add(id);
                this.hits++;
            }
            this.requests++;
        } else {
            this.addToSlabClass(sc, id, size);
            
            this.requests++;
        }
    }

    // driver function for looping through trace
    public void calculateHitRate() {
        this.initCache();
        
        BufferedReader trace = this.openTrace();
        try {
            String line;
            while((line = trace.readLine()) != null) {
                this.processLine(line);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void printResults() {
        System.out.println("Finished determining hits!");
        System.out.printf("HITS: %d\n", this.hits);
        System.out.printf("REQUESTS: %d\n", this.requests);
        System.out.printf("HIT RATE: %f\n", ((double) this.hits) / this.requests);
        
        // System.out.print("SPACE ALLOCATION: ");
        // System.out.println(this.cacheUsedSpace);
        
        // for (int sc : this.slabAllocation.keySet()) {
        //     this.slabAllocation.put(sc, this.slabAllocation.get(sc) * SLAB_SIZE);
        // }
        // System.out.print("ALLOWED SPACE: ");
        // System.out.println(this.slabAllocation);        
    }

    public static void main(String[] args){  
        System.out.println("Initializing cache and counting hits!");  

        CacheDynamic currCache = new CacheDynamic();
        currCache.calculateHitRate();
        currCache.printResults();
    }

}