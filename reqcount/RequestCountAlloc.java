/* 
    Determines a slab allocation using request-count analysis 

    Simply count the number of requests for each slab in each epoch and move 
    slabs to commonly-requested slab classes to unused ones. 

    Naively always moves, without considering the cost of moving (evicting items
    currently in the slab). Will add this consideration in the future. 

    Slightly different from HRC analysis previously done. HRC analysis, in 
    each epoch, for each slab class, computed the "HRC", which mapped each 
    possible cache size "n" to the number of items with stack distance <= n (
    since if the stack distance is smaller than a size, the cache will get a 
    hit). Then, the "benefit" of moving to a slab class was the gain in items
    that would get a hit by moving from cache_size to cache_size+slab. 

    This strategy purely just looks at the number of requests for each slab 
    class, and it will add in additional cost analysis later. 

    Returns an ArrayList<HashMap<Integer, Integer>> describing how many slabs
    should be allocated for each slab class at each epoch. 
 */

import java.util.*;
import java.io.*;
import java.nio.*;

public class RequestCountAlloc {

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
    HashMap<Integer, Integer> numRequestsSC;   // maps sc -> #requests

    ArrayList<HashMap<Integer, Integer>> traceSlabAllocations;

    // constructor
    public RequestCountAlloc(String traceLink, String resultLink) {
        // class variables
        this.t = 1;
        this.traceLink = traceLink;
        this.numLines = new File(this.traceLink).length() / 24;
        this.resultLink = resultLink;
        this.numRequestsSC = new HashMap<Integer, Integer>();
        for (int sc : SLAB_CLASSES) {
            this.numRequestsSC.put(sc, 0);
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

        // update requests counts
        int sc = getSlabClass(line.size);
        this.numRequestsSC.put(sc, this.numRequestsSC.get(sc) + 1);
    }

    public void moveSlab(int min, int max) {
        this.SLAB_COUNTS_MAP.put(max, this.SLAB_COUNTS_MAP.get(max) + 1);
        this.SLAB_COUNTS_MAP.put(min, this.SLAB_COUNTS_MAP.get(min) - 1);
    }

    public void reviewEpochAndRealloc() {
        try {
            // calculate the min SC (where the SLAB_COUNTS is still > 0) and max SC
            int min = -1;
            int minNumSlabs = -1;
            int max = -1;
            int maxReqCount = 0;
            int minReqCount = Integer.MAX_VALUE;

            for (int sc : SLAB_CLASSES) {
                if (this.numRequestsSC.get(sc) >= maxReqCount) {
                    max = sc;
                    maxReqCount = numRequestsSC.get(sc);
                } 
                if (numRequestsSC.get(sc) <= minReqCount 
                    && this.SLAB_COUNTS_MAP.get(sc) > minNumSlabs
                    && this.SLAB_COUNTS_MAP.get(sc) > 0) {
                    min = sc;
                    minReqCount = numRequestsSC.get(sc);
                    minNumSlabs = this.SLAB_COUNTS_MAP.get(sc);
                }
            }

            // move a slab from min to max slab class
            String moved = "";
            if (min != -1 && min != max 
                && this.numRequestsSC.get(min) * THRESHOLD_MULTIPLIER 
                    < this.numRequestsSC.get(max)) {
            // if (min != -1 && min != max) {
                if (this.SLAB_COUNTS_MAP.get(min) != 0) {
                    moveSlab(min, max);
                    moved = " (moved)";
                }
            }   
            this.traceSlabAllocations.add((HashMap<Integer, Integer>) 
                                            SLAB_COUNTS_MAP.clone());
            
            // write the hit rate over the last epoch to file
            // this.writer.write("epoch " + String.valueOf(this.t / LINES_READ_PER_CHUNK) + moved + "\n");
            // if (!moved.equals("")) {
            //     this.writer.write(String.format("moved slab from %d to %d\n", min, max));
            // }
            // this.writer.write("requests counts: " + this.numRequestsSC.toString() + "\n");
            // this.writer.write("slab counts: " + this.SLAB_COUNTS_MAP.toString() + "\n");
            // this.writer.write(String.format("epoch hitrate: %f\n", (float) this.epochhits / (float) LINES_READ_PER_CHUNK ));
            // this.writer.write(String.format("lifetime hitrate: %f\n", (float) this.lifetimehits / (float) this.t));    
            // this.writer.write("\n");

            // reset epoch data structures
            this.numRequestsSC = new HashMap<Integer, Integer>();
            for (int sc : SLAB_CLASSES) {
                this.numRequestsSC.put(sc, 0);
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("error processing epoch and collecting stats");
        }
    }

    public float processTrace() {
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
        return (float) this.lifetimehits / (float) this.t;
    }

    // example how to run
    public static void main(String[] args) throws Exception {
        RequestCountAlloc alloc = new RequestCountAlloc("/mntData2/jason/cphy/w13.oracleGeneral.bin", 
                "./reqcount_results/w13.reqcount.txt");
        alloc.processTrace();
    }
}

