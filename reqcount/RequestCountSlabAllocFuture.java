/* 
    Determines a slab allocation using request-count analysis 

    Keep track of all items requested over the course of an epoch. Divide the 
    objects in each slab class into groups of SLAB_SIZE, ordered by LRU. 
    Define the "utilization" of a slab to be the number of requests for 
    items within that slab. Considering the current slab allocation, move the 
    slab with the lowest utilization to the slab class with highest utilization 
    on its lowest-utilization slab.  

    Returns an ArrayList<HashMap<Integer, Integer>> describing how many slabs
    should be allocated for each slab class at each epoch. 
 */

import java.util.*;
import java.io.*;
import java.nio.*;

public class RequestCountSlabAllocFuture {

    public static class TraceLine {
        // static variables
        static byte[] id_buff = new byte[8];
        static byte[] size_buff = new byte[4];
        static byte[] next_time_buff = new byte[8];
    
        // class variables
        long id;
        int size;
        long next_time;

        // constructor: parse numbers from whatever is stored in static arrs
        public TraceLine() {
            ByteBuffer bb;

            bb = ByteBuffer.wrap(id_buff);
            bb.order( ByteOrder.LITTLE_ENDIAN );
            this.id = bb.getLong();

            bb = ByteBuffer.wrap(size_buff);
            bb.order( ByteOrder.LITTLE_ENDIAN );
            this.size = bb.getInt();

            bb = ByteBuffer.wrap(next_time_buff);
            bb.order( ByteOrder.LITTLE_ENDIAN );
            this.next_time = bb.getLong();
        }

        // constructor: manually parse line from values and set here
        public TraceLine(long id, int size, long next_time) {
            this.id = id;
            this.size = size;
            this.next_time = next_time;
        }
    }

    // static variables: are 15 slab classes for now
    public static int[] SLAB_CLASSES = { 64, 128, 256, 512, 1024, 2048, 4096, 8192, 
        16384, 32768, 65536, 131072, 262144, 524288, 1048576};
    public static int[] SLAB_COUNTS = { 16, 8, 8, 4, 4, 3, 3, 2, 2, 2, 1, 1, 1, 1, 1 };
    public static int LINES_READ_PER_CHUNK = 540000;
    // public static int LINES_READ_PER_CHUNK = 4218;
    
    static int SLAB_SIZE = 1048576;
    static int THRESHOLD_MULTIPLIER = 3;

    // class variables
    int t;
    long numLines;
    String traceLink;
    String resultLink;
    HashMap<Integer, Integer> SLAB_COUNTS_MAP;
    BufferedInputStream reader;
    BufferedWriter writer;

    HashMap<Integer, LinkedHashMap<Long, Integer>> LRUPerSC;  // maps sc -> LL of { long => num_requests }
    ArrayList<HashMap<Integer, Integer>> traceSlabAllocations;

    // constructor
    public RequestCountSlabAllocFuture(String traceLink, String resultLink) {
        // class variables
        this.t = 1;
        this.traceLink = traceLink;
        this.resultLink = resultLink;
        this.numLines = new File(this.traceLink).length() / 24;
        this.LRUPerSC = new HashMap<>();
        for (int sc : SLAB_CLASSES) {
            this.LRUPerSC.put(sc, new LinkedHashMap<>());
        }

        // init readers and writers 
        try {
            this.reader = new BufferedInputStream(new FileInputStream(new File(this.traceLink)));
            this.writer = new BufferedWriter(new FileWriter(this.resultLink));
        } catch (IOException e) {
            e.printStackTrace();
        }

        // set initial slab counts
        this.SLAB_COUNTS_MAP = new HashMap<>();
        for (int i = 0; i < SLAB_CLASSES.length; i++) {
            this.SLAB_COUNTS_MAP.put(SLAB_CLASSES[i], SLAB_COUNTS[i]);
        }
        this.traceSlabAllocations = new ArrayList<>();
    }

    // static methods
    public static int getSlabClass(int size) {
        int low = 0;
        int high = SLAB_CLASSES.length;
        while (low < high) {
            int mid = (low + high) / 2;
            if (SLAB_CLASSES[mid] == size) {
                return SLAB_CLASSES[mid];
            } else if (size < SLAB_CLASSES[mid]) {
                high = mid;
            } else {
                low = mid + 1;
            }
        }       
        return low < SLAB_CLASSES.length ? SLAB_CLASSES[low] : SLAB_CLASSES[SLAB_CLASSES.length - 1];
    }

    // class methods
    public TraceLine readTraceLine() throws IOException{
        // discard the first 4 bytes (actual time)
        if (this.reader.read(TraceLine.size_buff) == -1) return null;

        // read the next fields from the trace
        if (this.reader.read(TraceLine.id_buff) == -1) return null;
        if (this.reader.read(TraceLine.size_buff) == -1) return null;
        if (this.reader.read(TraceLine.next_time_buff) == -1) return null;
        return new TraceLine();
    }
    
    public void processLine(TraceLine line) {
        // if the item isn't going to fit in any slab class, just ignore it
        if (line.size > SLAB_SIZE) return;

        // update LRU
        int sc = getSlabClass(line.size);
        LinkedHashMap<Long, Integer> currLRU = this.LRUPerSC.get(sc);
        int prev_counts = currLRU.getOrDefault(line.id, 0);
        currLRU.remove(line.id);
        currLRU.put(line.id, prev_counts + 1);
    }

    public void moveSlab(int min, int max) {
        this.SLAB_COUNTS_MAP.put(max, this.SLAB_COUNTS_MAP.get(max) + 1);
        this.SLAB_COUNTS_MAP.put(min, this.SLAB_COUNTS_MAP.get(min) - 1);
    }

    public float getMinSlabUtil(ArrayList<Integer> sortedValues, int sc) {
        // LinkedHashMap<Long, Integer> currLRU = this.LRUPerSC.get(sc);
        // Iterator<Integer> iter = currLRU.values().iterator();

        Iterator<Integer> iter = sortedValues.iterator();

        int numSlabs = SLAB_COUNTS_MAP.get(sc);
        if (numSlabs == 0) return -1;

        long minUtil = Long.MAX_VALUE;
        boolean done = false;
        /* loop through slabs and calculate utilization for each */
        for (int i = 0; i < numSlabs; i++) {
            long currSlabUtil = 0;
            for (int j = 0; j < SLAB_SIZE / sc; j++) {
                if (iter.hasNext()) {
                    currSlabUtil += (long) iter.next();
                } else {
                    done = true;
                    break;
                }
            }
            if (currSlabUtil < minUtil) minUtil = currSlabUtil;

            if (done) {
                if (i < numSlabs - 1) minUtil = 0;
                break;
            }
        }
        return (float) minUtil;
    }   

    public float getNextSlabUtil(ArrayList<Integer> sortedValues, int sc) {
        int numSlabs = SLAB_COUNTS_MAP.get(sc);

        // LinkedHashMap<Long, Integer> currLRU = this.LRUPerSC.get(sc);
        // Iterator<Integer> iter = currLRU.values().iterator();

        Iterator<Integer> iter = sortedValues.iterator();
        
        boolean done = false;
        /* loop through first (SLAB_SIZE/sc) * numSlabs items; basically discard */
        for (int i = 0; i < numSlabs; i++) {
            for (int j = 0; j < SLAB_SIZE / sc; j++) {
                if (iter.hasNext()) { 
                    iter.next(); 
                } else {
                    done = true;
                    break;
                }
            }
            if (done) break;
        }

        /* loop through one more slab and return the util of this slab */
        if (done) {
            /* do not exist any more items in the slab class */
            return 0;
        } 

        long nextUtil = 0;
        for (int j = 0; j < SLAB_SIZE / sc; j++) {
            if (iter.hasNext()) { 
                nextUtil += iter.next(); 
            } else { 
                break;
            }
        }
        return (float) nextUtil;
    }

    public void reviewEpochAndRealloc() {
        try {
            /* sort each of the reqcounts */
            HashMap<Integer, ArrayList<Integer>> sortedReqcounts 
                = new HashMap<>();
            for (int sc : SLAB_CLASSES) {
                ArrayList<Integer> sortedValues = new ArrayList<>(this.LRUPerSC.get(sc).values());
                Collections.sort(sortedValues);
                sortedReqcounts.put(sc, sortedValues);
            }
            
            /* find the slab with the lowest utilization, and lowest-utilization 
             * slab in a slab class, with the highest utilization 
            */
            float minUtil = Long.MAX_VALUE;
            int minUtilSC = -1;
            for (int sc : SLAB_CLASSES) {
                float currMinUtil = getMinSlabUtil(sortedReqcounts.get(sc), sc);
                if (currMinUtil == -1) continue;

                if (currMinUtil < minUtil) {
                    minUtil = currMinUtil;
                    minUtilSC = sc;
                }
            }

            float highestLastUtil = -1;
            int highestLastUtilSC = -1;
            HashMap<Integer, Float> highestNextSlabs = new HashMap<>();
            for (int sc : SLAB_CLASSES) {
                float currLastUtil = getNextSlabUtil(sortedReqcounts.get(sc), sc);
                /* if lastUtil is 0, then will not benefit from move anyways */
                highestNextSlabs.put(sc, currLastUtil);

                if (currLastUtil == 0) continue;

                if (currLastUtil > highestLastUtil) {
                    highestLastUtil = currLastUtil;
                    highestLastUtilSC = sc;
                }
            }

            // long highestLastUtil = -1;
            // int highestLastUtilSC = -1;

            // for (int sc : SLAB_CLASSES) {
            //     long currMinUtil = getMinUtil(sc);
            //     if (currMinUtil == -1) continue;

            //     if (currMinUtil < minUtil) {
            //         minUtil = currMinUtil;
            //         minUtilSC = sc;
            //     } else if (currMinUtil > highestLastUtil) {
            //         highestLastUtil = currMinUtil;
            //         highestLastUtilSC = sc;
            //     }
            // }

            // move a slab from min to max slab class
            String moved = "";
            if (minUtilSC != -1
                && highestLastUtilSC != -1
                && minUtilSC != highestLastUtilSC
                && this.SLAB_COUNTS_MAP.get(minUtilSC) != 0) {
                moveSlab(minUtilSC, highestLastUtilSC);
                moved = " (moved)";
            }
            this.traceSlabAllocations.add((HashMap<Integer, Integer>) 
                                            SLAB_COUNTS_MAP.clone());

                                
            // write the hit rate over the last epoch to file
            this.writer.write("epoch " + String.valueOf(this.t / LINES_READ_PER_CHUNK) + moved + "\n");
            if (!moved.equals("")) this.writer.write(String.format("moved slab from %d (%f) to %d (%f)\n", minUtilSC, minUtil, highestLastUtilSC, highestLastUtil));
            // // this.writer.write("requests counts: " + this.numRequestsSC.toString() + "\n");
            this.writer.write("slab counts: " + this.SLAB_COUNTS_MAP.toString() + "\n");
            this.writer.write("highest nexts: " + highestNextSlabs.toString() + "\n");
            this.writer.write("\n");
            
            // reset epoch data structures

            this.LRUPerSC = new HashMap<>();
            for (int sc : SLAB_CLASSES) this.LRUPerSC.put(sc, new LinkedHashMap<>());
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("error processing epoch and collecting stats");
        }
    }

    public ArrayList<HashMap<Integer, Integer>> processTrace() {
        try {
            TraceLine line;
            while((line = this.readTraceLine()) != null) {
                if (this.t % LINES_READ_PER_CHUNK == 0) {
                    this.reviewEpochAndRealloc();
                }
                this.processLine(line);
                this.t++;
            }
            this.writer.close();
            this.reader.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return this.traceSlabAllocations;
    }

    // example how to run
    public static void main(String[] args) throws Exception {
        RequestCountSlabAllocFuture alloc = new RequestCountSlabAllocFuture("/mntData2/jason/cphy/w01.oracleGeneral.bin", 
                "./reqcount_slab_results/trash4.txt"); 
                // "./reqcount_slab_results/w01_alloc.txt"); 

        System.out.println(alloc.processTrace());
    }
}

