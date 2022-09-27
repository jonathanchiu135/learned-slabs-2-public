/* 
 * Simulates a static cache on the specified trace 
 */

import java.util.*;
import java.io.*;
import java.nio.file.*;


public class CacheSimulatorStatic {
    
    public static int[] SLAB_SIZES = {64, 128, 256, 512, 1024, 2048, 4096, 8192, 
        16384, 32768, 65536, 131072, 262144, 524288, 1048576};
    public static int SLAB_SIZE = 1048576;
    public static String traceLink = "./traces/empty-test.txt";
    public static String allocationLink = "./results/empty-test-alloc.txt";

    public int hits;
    public int requests;
    public HashMap<Integer, Integer> slabAllocation;

    public HashMap<Integer, Integer> cacheUsedSpace;
    public HashMap<Integer, Integer> cacheContents; // maps id to size
    public HashMap<Integer, LinkedHashSet<Integer>> cacheLRU;

    /* 
        Constructor
    */
    public CacheSimulatorStatic() {
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
            BufferedReader allocationFile = Files.newBufferedReader(Paths.get(allocationLink));
            String line = allocationFile.readLine();
            line = line.substring(1, line.length() - 1);

            String[] keyVals = line.split(", ");
            for(String keyVal : keyVals ){
                String[] parts = keyVal.split("=", 2);
                int sc = Integer.parseInt(parts[0]);
                int slabs = Integer.parseInt(parts[1]); 
                this.slabAllocation.put(sc, slabs);
            }        
        } catch (Exception e) {
            e.printStackTrace();
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

    public void addToSlabClass(int sc, int id, int size) {
        // if there is just not enough space to hold the item, just don't store it
        if (size > this.slabAllocation.get(sc) * SLAB_SIZE) {
            return;
        }

        LinkedHashSet<Integer> currSet = this.cacheLRU.get(sc);
        Iterator<Integer> currIt = currSet.iterator();

        // remove from the slab class until there is enough space
        while (this.slabAllocation.get(sc) * SLAB_SIZE - this.cacheUsedSpace.get(sc) < size) {
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

        if (this.cacheContents.containsKey(id)) {
            System.out.print(sc + " ");
            System.out.println("HIT: " + id);
            int oldSize = this.cacheContents.get(id);
            if (size != oldSize) {
                // if the size of the item has changed, then remove it from the old and add again
                int oldSC = getSlabClass(oldSize);
                this.cacheUsedSpace.put(oldSC, this.cacheUsedSpace.get(oldSC) - oldSize);   
                this.cacheLRU.get(oldSC).remove(id);

                this.addToSlabClass(sc, id, size);
            } else {
                this.cacheLRU.get(sc).remove(id);
                this.cacheLRU.get(sc).add(id);
                this.hits++;
            }
            this.requests++;
        } else {
            System.out.print(sc + " ");
            System.out.println("MISS: " + id);
            this.addToSlabClass(sc, id, size);
            
            this.requests++;
        }
    }

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
    }

    public static void main(String[] args){  
        System.out.println("Initializing cache and counting hits!");  

        // going to need to make sure this all works + redo btw
        // CacheStatic currCache = new CacheStatic();
        // currCache.calculateHitRate();
        // currCache.printResults();
    }

}