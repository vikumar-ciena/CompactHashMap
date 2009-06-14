import java.util.*;
import java.io.*;

/**
 * Hash table based implementation of the <tt>Map</tt> interface.  This
 * implementation provides all of the optional map operations, and permits
 * <tt>null</tt> values and the <tt>null</tt> key.  (The <tt>HashMap</tt>
 * class is roughly equivalent to <tt>Hashtable</tt>, except that it is
 * unsynchronized and permits nulls.)  This class makes no guarantees as to
 * the order of the map; in particular, it does not guarantee that the order
 * will remain constant over time.
 *
 * <p>This implementation provides constant-time performance for the basic
 * operations (<tt>get</tt> and <tt>put</tt>), assuming the hash function
 * disperses the elements properly among the buckets.  Iteration over
 * collection views requires time proportional to the "capacity" of the
 * <tt>HashMap</tt> instance (the number of buckets) plus its size (the number
 * of key-value mappings).  Thus, it's very important not to set the initial
 * capacity too high (or the load factor too low) if iteration performance is
 * important.
 *
 * <p>An instance of <tt>HashMap</tt> has two parameters that affect its
 * performance: <i>initial capacity</i> and <i>load factor</i>.  The
 * <i>capacity</i> is the number of buckets in the hash table, and the initial
 * capacity is simply the capacity at the time the hash table is created.  The
 * <i>load factor</i> is a measure of how full the hash table is allowed to
 * get before its capacity is automatically increased.  When the number of
 * entries in the hash table exceeds the product of the load factor and the
 * current capacity, the hash table is <i>rehashed</i> (that is, internal data
 * structures are rebuilt) so that the hash table has approximately twice the
 * number of buckets.
 *
 * <p>As a general rule, the default load factor (.75) offers a good tradeoff
 * between time and space costs.  Higher values decrease the space overhead
 * but increase the lookup cost (reflected in most of the operations of the
 * <tt>HashMap</tt> class, including <tt>get</tt> and <tt>put</tt>).  The
 * expected number of entries in the map and its load factor should be taken
 * into account when setting its initial capacity, so as to minimize the
 * number of rehash operations.  If the initial capacity is greater
 * than the maximum number of entries divided by the load factor, no
 * rehash operations will ever occur.
 *
 * <p>If many mappings are to be stored in a <tt>HashMap</tt> instance,
 * creating it with a sufficiently large capacity will allow the mappings to
 * be stored more efficiently than letting it perform automatic rehashing as
 * needed to grow the table.
 *
 * <p><strong>Note that this implementation is not synchronized.</strong>
 * If multiple threads access a hash map concurrently, and at least one of
 * the threads modifies the map structurally, it <i>must</i> be
 * synchronized externally.  (A structural modification is any operation
 * that adds or deletes one or more mappings; merely changing the value
 * associated with a key that an instance already contains is not a
 * structural modification.)  This is typically accomplished by
 * synchronizing on some object that naturally encapsulates the map.
 *
 * If no such object exists, the map should be "wrapped" using the
 * {@link Collections#synchronizedMap Collections.synchronizedMap}
 * method.  This is best done at creation time, to prevent accidental
 * unsynchronized access to the map:<pre>
 *   Map m = Collections.synchronizedMap(new HashMap(...));</pre>
 *
 * <p>The iterators returned by all of this class's "collection view methods"
 * are <i>fail-fast</i>: if the map is structurally modified at any time after
 * the iterator is created, in any way except through the iterator's own
 * <tt>remove</tt> method, the iterator will throw a
 * {@link ConcurrentModificationException}.  Thus, in the face of concurrent
 * modification, the iterator fails quickly and cleanly, rather than risking
 * arbitrary, non-deterministic behavior at an undetermined time in the
 * future.
 *
 * <p>Note that the fail-fast behavior of an iterator cannot be guaranteed
 * as it is, generally speaking, impossible to make any hard guarantees in the
 * presence of unsynchronized concurrent modification.  Fail-fast iterators
 * throw <tt>ConcurrentModificationException</tt> on a best-effort basis.
 * Therefore, it would be wrong to write a program that depended on this
 * exception for its correctness: <i>the fail-fast behavior of iterators
 * should be used only to detect bugs.</i>
 *
 * <p>This class is a member of the
 * <a href="{@docRoot}/../technotes/guides/collections/index.html">
 * Java Collections Framework</a>.
 *
 * @param <K> the type of keys maintained by this map
 * @param <V> the type of mapped values
 *
 * @author  Doug Lea
 * @author  Josh Bloch
 * @author  Arthur van Hoff
 * @author  Neal Gafter
 * @author  Alex Yakovlev
 * @see     Object#hashCode()
 * @see     Collection
 * @see     Map
 * @see     TreeMap
 * @see     Hashtable
 * @since   1.2
 */
public class FastHashMap<K,V>
    implements Cloneable, Serializable, Map<K,V>
{
    /**
     * True if this map contains null key.
     * This makes iteration faster:
     * null key in table == empty cell,
     * no need for index table lookup.
     */
    transient boolean nullKeyPresent;

    /**
     * The default initial capacity - MUST be a power of two.
     */
    static final int DEFAULT_INITIAL_CAPACITY = 4;

    /**
     * The load factor used when none specified in constructor.
     */
    static final float DEFAULT_LOAD_FACTOR = 0.75f;

    /**
     * Maximum allowed load factor, since element index bits
     * cannot exceed number of hash bits (other bits are used to store hashcode).
     */
    static final float MAXIMUM_LOAD_FACTOR = 1f;

    /**
     * Bits available to store indices and hashcode bits.
     * Now the highest (31st) bit (negative/inverted values) is used as deleted flag,
     * 30th bit is used to mark end of list, thus 30 bits are available.
     */
    final static int AVAILABLE_BITS = 0x3FFFFFFF;

    /**
     * Bits with control information on where to look for next entry in hash bin.
     */
    final static int CONTROL_BITS     = 0xC0000000;

    /**
     * This bits are used to mark empty cell.
     * Also, it's used in 'next' cell, and index must not be zero.
     * Used only in main hashtable, never in overflow.
     */
    final static int CONTROL_EMPTY    = 0;

    /**
     * This bits are used only in main hashtable,
     * (when next cell is still empty) never in overflow.
     */
    final static int CONTROL_NEXT     = 0x40000000;

    /**
     * Next element is in overflow table.
     */
    final static int CONTROL_OVERFLOW = 0x80000000;

    /**
     * This bits marks 'end of list'.
     */
    final static int CONTROL_END      = 0xC0000000;

    /**
     * The maximum capacity, used if a higher value is implicitly specified
     * by either of the constructors with arguments.
     * MUST be a power of two <= 1<<30.
     */
    static final int MAXIMUM_CAPACITY = 1 << 30;

    /**
     * Applies a supplemental hash function to a given object's hashCode,
     * which defends against poor quality hash functions. This is critical
     * because HashMap uses power-of-two length hash tables, that
     * otherwise encounter collisions for hashCodes that do not differ
     * in lower bits. Note: Null keys always map to hash 0, thus index 0.
     */
    final static int hash(int h) {
        // This function ensures that hashCodes that differ only by
        // constant multiples at each bit position have a bounded
        // number of collisions (approximately 8 at default load factor).
        h ^= (h >>> 20) ^ (h >>> 12);
        return h ^ (h >>> 7) ^ (h >>> 4);
    }

    /**
     * Return improved hash for object o.
    final static int hash(Object o) {
        return o == null ? 0 : hash(o.hashCode());
    }
     */

    /**
     * The number of key-value mappings contained in this map.
     */
    transient int size = 0;

    /**
     * Arrays with stored keys and values.
     * Storing them in one array in neighbour cells
     * is faster since it's reading adjacent memory addresses.
     */
    transient Object[] keyValueTable;

    /**
     * 1 if keyValueTable contains keys and values,
     * 0 if only keys (to save memory in HashSet).
     */
    transient int keyIndexShift;

    /**
     * Value to use if keyIndexShift is 0.
     */
    final static Object DUMMY_VALUE = new Object();

    /**
     * Array of complex indices.
     *
     * First <tt>hashLen</tt> are hashcode-to-array maps,
     * next <tt>threshold</tt> maps to next element with the same hashcode.
     * Highest index bit (negative/inverted values) is used as deleted flag,
     * 30th bit is used to mark last element in list,
     * lowest bits are real index in array,
     * and in the middle hashcode bits is stored.
     *
     * Because of new arrays are initialised with zeroes,
     * and we want to minimise number of memory writes,
     * we leave 0 as value of 'unoccupied' entry,
     * and invert real indices values.
     * We also need to store deleted entries list,
     * and to easily check if entry is occupied or not during iteration
     * deleted indices are not inverted and stored as positive,
     * but to separate them from default zero value we add 1 to them.
     */
    transient private int[] indexTable;

    /**
     * Index of the first not occupied position in array.
     * All elements starting with this index are free.
     */
    transient int firstEmptyIndex = 0;

    /**
     * Index of first element in deleted list,
     * or -1 if no elements are deleted.
     */
    transient private int firstDeletedIndex = -1;

    /**
     * Number of hash baskets, power of 2.
     */
    transient private int hashLen;

    /**
     * The next size value at which to resize (capacity * load factor).
     * @serial
     */
    int threshold;

    /**
     * The load factor for the hash table.
     * @serial
     */
    final float loadFactor;

    /**
     * The number of times this HashMap has been structurally modified
     * Structural modifications are those that change the number of mappings in
     * the HashMap or otherwise modify its internal structure (e.g.,
     * rehash).  This field is used to make iterators on Collection-views of
     * the HashMap fail-fast.  (See ConcurrentModificationException).
     */
    transient int modCount;

    /**
     * Constructs an empty <tt>HashMap</tt> with the default initial capacity
     * (16) and the default load factor (0.75).
     */
    public FastHashMap() {
       this(true);
    }

    /**
     * Constructor to be used in HashSet
     * containing only keys without values
     * thus saving some memory if withValues is false.
     */
    FastHashMap(boolean withValues) {
        loadFactor = DEFAULT_LOAD_FACTOR;
        hashLen = DEFAULT_INITIAL_CAPACITY;
        threshold = (int)(hashLen * loadFactor);
        keyIndexShift = withValues ? 1 : 0;
        init();
    }

    /**
     * Constructs an empty <tt>HashMap</tt> with the specified initial
     * capacity and load factor.
     *
     * @param  initialCapacity the initial capacity
     * @param  loadFactor      the load factor
     * @throws IllegalArgumentException if the initial capacity is negative
     *         or the load factor is greater than one or is too low
     */
    public FastHashMap(int initialCapacity, float loadFactor) {
        this(initialCapacity, loadFactor, true);
    }

    /**
     * Constructor to be used in HashSet
     * containing only keys without values
     * thus saving some memory if withValues is false.
     */
    FastHashMap(int initialCapacity, float loadFactor, boolean withValues) {
        if (initialCapacity < 0)
            throw new IllegalArgumentException(
                "Illegal initial capacity: " + initialCapacity);
        if (initialCapacity > MAXIMUM_CAPACITY)
            initialCapacity = MAXIMUM_CAPACITY;
        if (Float.isNaN(loadFactor))
            throw new IllegalArgumentException(
                "Illegal load factor: " + loadFactor);
        this.loadFactor = loadFactor > MAXIMUM_LOAD_FACTOR ? MAXIMUM_LOAD_FACTOR : loadFactor;
        // Find a power of 2 >= initialCapacity
        for (hashLen = DEFAULT_INITIAL_CAPACITY; hashLen < initialCapacity; hashLen <<= 1);
        threshold = (int)(hashLen * loadFactor);
        if (threshold < 1)
            throw new IllegalArgumentException(
                "Illegal load factor: " + loadFactor);
        keyIndexShift = withValues ? 1 : 0;
        init();
    }

    /**
     * Constructs an empty <tt>HashMap</tt> with the specified initial
     * capacity and the default load factor (0.75).
     *
     * @param  initialCapacity the initial capacity.
     * @throws IllegalArgumentException if the initial capacity is negative.
     */
    public FastHashMap(int initialCapacity) {
        this(initialCapacity, DEFAULT_LOAD_FACTOR, true);
    }

    /**
     * Constructor to be used in HashSet
     * containing only keys without values
     * thus saving some memory if withValues is false.
     */
    FastHashMap(int initialCapacity, boolean withValues) {
        this(initialCapacity, DEFAULT_LOAD_FACTOR, withValues);
    }

    /**
     * Constructs a new <tt>HashMap</tt> with the same mappings as the
     * specified <tt>Map</tt>.  The <tt>HashMap</tt> is created with
     * default load factor (0.75) and an initial capacity sufficient to
     * hold the mappings in the specified <tt>Map</tt>.
     *
     * @param   m the map whose mappings are to be placed in this map
     * @throws  NullPointerException if the specified map is null
     */
    public FastHashMap(Map<? extends K, ? extends V> m) {
        this(Math.max((int) (m.size() / DEFAULT_LOAD_FACTOR) + 1,
                      DEFAULT_INITIAL_CAPACITY), DEFAULT_LOAD_FACTOR);
        for (Map.Entry<? extends K, ? extends V> e : m.entrySet())
            put(e.getKey(), e.getValue(), false);
    }

    /**
     * Initialization hook for subclasses. This method is called
     * in all constructors and pseudo-constructors (clone, readObject)
     * after HashMap has been initialized but before any entries have
     * been inserted.  (In the absence of this method, readObject would
     * require explicit knowledge of subclasses.)
     */
    void init() {
    }

    /**
     * Increase size of internal arrays.
     *
     * @param  newCapacity  must be power of two
     * and greater than current capacity (hashLen).
     */
    void resize(int newCapacity) {
        // New storage allocation
        int newValueLen = (int)(newCapacity * loadFactor);
        Object[] newKeyValues;
        if (keyValueTable != null)
            newKeyValues = Arrays.copyOf(keyValueTable, (newValueLen<<keyIndexShift)+1);
        else
            newKeyValues = new Object[newValueLen<<keyIndexShift];
        int[] newIndices = new int[newCapacity+newValueLen];
        if (indexTable != null) {
            int mask = AVAILABLE_BITS ^ (hashLen-1);
            int newMask = AVAILABLE_BITS ^ (newCapacity-1);
            for (int i = 0; i < hashLen; i++) {
                int j = indexTable[i];
                if ((j & CONTROL_BITS) == CONTROL_EMPTY) continue;
                if ((j & CONTROL_BITS) == CONTROL_NEXT) {
                    int arrayIndex1 = j & (hashLen-1);
                    int newHashIndex1 = i | (j & (newMask ^ mask));
                    int i2 = (i+1) & (hashLen-1);
                    int j2 = indexTable[i2];
                    int arrayIndex2 = j2 & (hashLen-1);
                    int newHashIndex2 = i | (j2 & (newMask ^ mask));
                    if (newHashIndex1 == newHashIndex2) {
                        newIndices[newHashIndex1] =
                            arrayIndex1 | (j  & newMask) | CONTROL_NEXT;
                        newIndices[(newHashIndex1+1)&(newCapacity-1)] =
                            arrayIndex2 | (j2 & newMask); // | CONTROL_EMPTY;
                    } else {
                        newIndices[newHashIndex1] = arrayIndex1 | (j  & newMask) | CONTROL_END;
                        newIndices[newHashIndex2] = arrayIndex2 | (j2 & newMask) | CONTROL_END;
                    }
                } else { // CONTROL_OVERFLOW and CONTROL_END
                    int next1i = -1, next1v = 0;
                    int next2i = -1, next2v = 0;
                    while (true) {
                        int arrayIndex = j & (hashLen-1);
                        int newHashIndex = i | (j & (newMask ^ mask));
                        if (newHashIndex == i) {
                            if (next1i >= 0) {
                                newIndices[next1i] = next1v | CONTROL_OVERFLOW;
                                next1i = newCapacity + (next1v & (newCapacity-1));
                            } else next1i = newHashIndex;
                            next1v = arrayIndex | (j & newMask);
                        } else if (newHashIndex == i+hashLen) {
                            if (next2i >= 0) {
                                newIndices[next2i] = next2v | CONTROL_OVERFLOW;
                                next2i = newCapacity + (next2v & (newCapacity-1));
                            } else next2i = newHashIndex;
                            next2v = arrayIndex | (j & newMask);
                        } else {
                            int newIndex = arrayIndex | (j & newMask);
                            int oldIndex = newIndices[newHashIndex];
                            if ((oldIndex & CONTROL_BITS) != CONTROL_EMPTY) {
                                newIndices[newCapacity + arrayIndex] = oldIndex;
                                newIndex |= CONTROL_OVERFLOW;
                            } else newIndex |= CONTROL_END;
                            newIndices[newHashIndex] = newIndex;
                        }
                        if ((j & CONTROL_BITS) == CONTROL_END) break;
                        j = indexTable[hashLen+arrayIndex];
                    }
                    if (next1i >= 0) newIndices[next1i] = next1v | CONTROL_END;
                    if (next2i >= 0) newIndices[next2i] = next2v | CONTROL_END;
                }
            }
            // Copy deleted list
            for (int i = firstDeletedIndex; i >= 0 && i != CONTROL_END;) {
                i = (newIndices[newCapacity + i] = indexTable[hashLen + i]);
            }
        }
        hashLen = newCapacity;
        threshold = newValueLen;
        keyValueTable = newKeyValues;
        indexTable = newIndices;
        // validate("Resize");
    }

    /**
     * Index of null key.
     */
    final static int NULL_INDEX = -1;

    /**
     * Index of 'not found' and 'end of iteration'.
     */
    final static int NO_INDEX = -2;

    /**
     * Returns the index of key in internal arrays if it is present.
     *
     * @param key key
     * @return index of key in array, -1 for null key or -2 if it was not found
     */
    final int positionOf(Object key) {
        // Null special case
        if (key == null)
            return nullKeyPresent ? NULL_INDEX : NO_INDEX;
        // Check arrays lazy allocation
        if (indexTable == null)
            return NO_INDEX;
        // Compute hash index
        int hc = hash(key.hashCode());
        int index = indexTable[hc & (hashLen-1)];
        // Empty?
        int control = index & CONTROL_BITS;
        if (control == CONTROL_EMPTY)
            return NO_INDEX;
        // Search
        int mask = AVAILABLE_BITS ^ (hashLen-1);
        while (true) {
            int position = index & (hashLen-1);
            if ((index & mask) == (hc & mask)) {
                Object key1 = keyValueTable[(position<<keyIndexShift)+1];
                if (key == key1 || key.equals(key1))
                    return position;
            }
            // Move forward
            if (control == CONTROL_END)
                return NO_INDEX; // END is more frequent - check it first
            else if (control == CONTROL_OVERFLOW)
                index = indexTable[hashLen+position];
            else if (control == CONTROL_NEXT)
                index = indexTable[(hc+1) & (hashLen-1)];
            else // CONTROL_EMPTY
                return NO_INDEX;
            control = index & CONTROL_BITS;
        }
    }

    /**
     * Returns the value to which the specified key is mapped,
     * or {@code null} if this map contains no mapping for the key.
     *
     * <p>More formally, if this map contains a mapping from a key
     * {@code k} to a value {@code v} such that {@code (key==null ? k==null :
     * key.equals(k))}, then this method returns {@code v}; otherwise
     * it returns {@code null}.  (There can be at most one such mapping.)
     *
     * <p>A return value of {@code null} does not <i>necessarily</i>
     * indicate that the map contains no mapping for the key; it's also
     * possible that the map explicitly maps the key to {@code null}.
     * The {@link #containsKey containsKey} operation may be used to
     * distinguish these two cases.
     *
     * @see #put(Object, Object)
     */
    @SuppressWarnings("unchecked")
    public V get(Object key) {
        // Null special case
        if (key == null)
            // HashSet (keyIndexShift==0) uses only containsKey
            return nullKeyPresent ? (V)keyValueTable[0] : null;
        // Check arrays lazy allocation
        if (indexTable == null)
            return null;
        // Compute hash index
        int hc = hash(key.hashCode());
        int index = indexTable[hc & (hashLen-1)];
        // Empty?
        int control = index & CONTROL_BITS;
        if (control == CONTROL_EMPTY)
            return null;
        // Search
        int mask = AVAILABLE_BITS ^ (hashLen-1);
        while (true) {
            int position = index & (hashLen-1);
            if ((index & mask) == (hc & mask)) {
                // HashSet (keyIndexShift==0) uses only containsKey
                Object key1 = keyValueTable[(position<<1)+1];
                if (key == key1 || key.equals(key1))
                    return (V)keyValueTable[(position<<1)+2];
            }
            // Move forward
            if (control == CONTROL_END)
                return null; // END is more frequent - check it first
            else if (control == CONTROL_OVERFLOW)
                index = indexTable[hashLen+position];
            else if (control == CONTROL_NEXT)
                index = indexTable[(hc+1) & (hashLen-1)];
            else // CONTROL_EMPTY
                return null;
            control = index & CONTROL_BITS;
        }
    }

    /**
     * Returns <tt>true</tt> if i-th array position
     * is not occupied (is in deleted elements list).
     *
     * @param i index in array, must be less than firstEmptyIndex
     * @return <tt>true</tt> if i-th is empty (was deleted)
     */
    final boolean isEmpty(int i) {
        return i == NULL_INDEX ? !nullKeyPresent :
            firstDeletedIndex >= 0 &&
            keyValueTable[(i<<keyIndexShift)+1] == null;
    }

    /**
     * Associates the specified value with the specified key in this map.
     * If the map previously contained a mapping for the key, the old
     * value is replaced.
     *
     * @param key key with which the specified value is to be associated
     * @param value value to be associated with the specified key
     * @return the previous value associated with <tt>key</tt>, or
     *         <tt>null</tt> if there was no mapping for <tt>key</tt>.
     *         (A <tt>null</tt> return can also indicate that the map
     *         previously associated <tt>null</tt> with <tt>key</tt>.)
     */
    public V put(K key, V value) {
        return put(key, value, true);
    }

    /**
     * Create a new key/value mapping without looking
     * if such key is already present in this map
     * if searchForExistingKey is false.
     */
    @SuppressWarnings("unchecked")
    final V put(K key, V value, boolean searchForExistingKey) {
        boolean callback = this instanceof FastLinkedHashMap;
        // Null special case
        if (key == null) {
            Object oldValue;
            if (keyIndexShift > 0) {
                if (keyValueTable == null)
                    keyValueTable = new Object[(threshold<<keyIndexShift)+1];
                oldValue = keyValueTable[0];
                keyValueTable[0] = value;
            } else oldValue = nullKeyPresent ? DUMMY_VALUE : null;
            if (nullKeyPresent) {
                if (callback) updateHook(NULL_INDEX);
            } else {
                nullKeyPresent = true;
                size++;
                if (callback) addHook(NULL_INDEX);
            }
            return (V)oldValue;
        }
        //
        int hc = hash(key.hashCode());
        int i = hc & (hashLen - 1);
        int head;
        if (indexTable != null) {
            head = indexTable[i];
        } else {
            head = 0;
            indexTable = new int[hashLen+threshold];
            if (keyValueTable == null)
                keyValueTable = new Object[(threshold<<keyIndexShift)+1];
        }
        // Check if this cell is occupied by another hash bin
        int control = head & CONTROL_BITS;
        if (control == CONTROL_EMPTY && head != 0) {
            int i2 = (hc-1) & (hashLen-1);
            int head2 = indexTable[i2];
            int j2 = head2 & (hashLen-1);
            indexTable[i2] = (head2 & AVAILABLE_BITS) | CONTROL_OVERFLOW;
            indexTable[hashLen + j2] = (head & AVAILABLE_BITS) | CONTROL_END;
            head = 0;
        }
        // Look if key is already in this map
        int depth = 1;
        int mask = AVAILABLE_BITS ^ (hashLen-1);
        if (control != CONTROL_EMPTY && searchForExistingKey) {
            int index = head;
            while (true) {
                int cur = index & (hashLen-1);
                if ((index & mask) == (hc & mask)) {
                    Object key1 = keyValueTable[(cur<<keyIndexShift)+1];
                    if (key == key1 || key.equals(key1)) {
                        Object oldValue;
                        if (keyIndexShift > 0) {
                            oldValue = keyValueTable[(cur<<keyIndexShift)+2];
                            keyValueTable[(cur<<keyIndexShift)+2] = value;
                        } else oldValue = DUMMY_VALUE;
                        if (callback) updateHook(cur);
                        return (V)oldValue;
                    }
                }
                depth++;
                if ((index & CONTROL_BITS) == CONTROL_END)
                    break;
                else if ((index & CONTROL_BITS) == CONTROL_OVERFLOW)
                    index = indexTable[hashLen+cur];
                else if ((index & CONTROL_BITS) == CONTROL_NEXT)
                    index = indexTable[(i+1) & (hashLen-1)];
                else // CONTROL_EMPTY
                    break;
            }
        }
        // Resize if needed
        boolean defragment = depth > 2 && firstEmptyIndex+depth <= threshold;
        if (size >= threshold) {
            resize(hashLen<<1);
            i = hc & (hashLen - 1);
            mask = AVAILABLE_BITS ^ (hashLen-1);
            head = indexTable[i];
            control = head & CONTROL_BITS;
            defragment = false;
        }
        // Find a place for new element
        int newIndex;
        if (firstDeletedIndex >= 0 && !defragment) {
            // First reuse deleted positions
            newIndex = firstDeletedIndex;
            firstDeletedIndex = indexTable[hashLen+firstDeletedIndex];
            if (firstDeletedIndex == CONTROL_END)
                firstDeletedIndex = -1;
            modCount++;
        } else {
            newIndex = firstEmptyIndex;
            firstEmptyIndex++;
        }
        // Defragment
        if (defragment) {
            // Move to new continuous space
            int j = head;
            head = (j & ~(hashLen-1)) | firstEmptyIndex;
            while (true) {
                int k = j & (hashLen - 1);
                Object tmp = keyValueTable[(k<<keyIndexShift)+1];
                keyValueTable[(firstEmptyIndex<<keyIndexShift)+1] = tmp;
                keyValueTable[(k<<keyIndexShift)+1] = null;
                if (keyIndexShift > 0) {
                    tmp = keyValueTable[(k<<keyIndexShift)+2];
                    keyValueTable[(firstEmptyIndex<<keyIndexShift)+2] = tmp;
                    keyValueTable[(k<<keyIndexShift)+2] = null;
                }
                int nextIndex, n;
                if ((j & CONTROL_BITS) == CONTROL_END) {
                    nextIndex = -1;
                    n = 0;
                } else if ((j & CONTROL_BITS) == CONTROL_OVERFLOW) {
                    nextIndex = hashLen+k;
                    n = indexTable[nextIndex];
                } else if ((j & CONTROL_BITS) == CONTROL_NEXT) {
                    nextIndex = (i+1) & (hashLen-1);
                    n = indexTable[nextIndex] | CONTROL_END;
                    indexTable[nextIndex] = 0;
                    head = (head & AVAILABLE_BITS) | CONTROL_OVERFLOW;
                    control = CONTROL_OVERFLOW;
                } else { // CONTROL_EMPTY
                    nextIndex = -1;
                    n = 0;
                }
                indexTable[hashLen+k] = firstDeletedIndex < 0 ?
                    CONTROL_END : firstDeletedIndex;
                firstDeletedIndex = k;
                if (callback) relocateHook(firstEmptyIndex, k);
                firstEmptyIndex++;
                if (nextIndex < 0) break;
                j = n;
                indexTable[hashLen + firstEmptyIndex - 1] =
                    (j & ~(hashLen-1)) | firstEmptyIndex;
            }
        }
        // Insert it
        keyValueTable[(newIndex<<keyIndexShift)+1] = key;
        if (keyIndexShift > 0)
            keyValueTable[(newIndex<<keyIndexShift)+2] = value;
        if (control == CONTROL_EMPTY) { // EMPTY is more frequent - check it first
            indexTable[i] = newIndex | (hc & mask) | CONTROL_END;
        } else if (control == CONTROL_END && newIndex != 0 && indexTable[(i+1)&(hashLen-1)] == 0) {
            indexTable[i] = (head & AVAILABLE_BITS) | CONTROL_NEXT;
            indexTable[(i+1)&(hashLen-1)] = newIndex | (hc & mask); // | CONTROL_EMPTY;
        } else if (control == CONTROL_NEXT) {
            int i2 = (i+1) & (hashLen-1);
            int head2 = indexTable[i2];
            indexTable[i2] = 0;
            indexTable[hashLen + (head & (hashLen-1))] = head2 | CONTROL_END;
            indexTable[hashLen + newIndex] = (head & AVAILABLE_BITS) | CONTROL_OVERFLOW;
            indexTable[i] = newIndex | (hc & mask) | CONTROL_OVERFLOW;
        } else { // CONTROL_OVERFLOW and CONTROL_END
            indexTable[hashLen + newIndex] = head;
            indexTable[i] = newIndex | (hc & mask) | CONTROL_OVERFLOW;
        }
        //
        size++;
        modCount++;
        if (callback) addHook(newIndex);
        // validate("Put "+key+" "+value);
        return null;
    }

    /**
     * Removes the mapping for the specified key from this map if present.
     *
     * @param  key key whose mapping is to be removed from the map
     * @return the previous value associated with <tt>key</tt>, or
     *         <tt>null</tt> if there was no mapping for <tt>key</tt>.
     *         (A <tt>null</tt> return can also indicate that the map
     *         previously associated <tt>null</tt> with <tt>key</tt>.)
     */
    public V remove(Object key) {
        V result = removeKey(key, NO_INDEX);
        return result == NOT_FOUND ? null : result;
    }

    /**
     * Value to distinguish null as 'key not found' from null as real value.
     */
    private final static Object NOT_FOUND = new Object();

    /**
     * Removes the mapping for the specified key from this map if present.
     *
     * @param key key whose mapping is to be removed from the map
     * @param index index of element to delete or NO_INDEX
     * @return NOT_FOUND or old value if index == NO_INDEX
     * if index != NO_INDEX return value is undefined (usually null)
     */
    @SuppressWarnings("unchecked")
    final V removeKey(Object key, int index) {
        // Null special case
        if (key == null) {
            if (nullKeyPresent) {
                nullKeyPresent = false;
                size--;
                if (this instanceof FastLinkedHashMap)
                    removeHook(NULL_INDEX);
                if (keyIndexShift > 0) {
                    V oldValue = (V)keyValueTable[0];
                    keyValueTable[0] = null;
                    return oldValue;
                } else return (V)DUMMY_VALUE;
            } else return (V)NOT_FOUND;
        }
        // Lazy array allocation check
        if (indexTable == null)
            return (V)NOT_FOUND;
        // Compute hash index
        int hc = hash(key.hashCode());
        int prev = -1;
        int curr = hc & (hashLen-1);
        // Check if this hash bin is empty
        int i = indexTable[curr];
        if ((i & CONTROL_BITS) == CONTROL_EMPTY)
            return (V)NOT_FOUND;
        // Search
        int mask = AVAILABLE_BITS ^ (hashLen-1);
        while (true) {
            int j = i & (hashLen-1);
            int k = hashLen + j;
            if ((hc & mask) == (i & mask)) {
                boolean found;
                if (index == NO_INDEX) {
                    Object o = keyValueTable[(j<<keyIndexShift)+1];
                    found = key == o || key.equals(o);
                } else
                    found = j == index;
                if (found) {
                    size--;
                    if((i & CONTROL_BITS) == CONTROL_OVERFLOW) {
                        indexTable[curr] = indexTable[k];
                    } else {
                        if ((i & CONTROL_BITS) == CONTROL_NEXT) {
                            int c2 = (curr+1) & (hashLen-1);
                            int i2 = indexTable[c2];
                            indexTable[curr] = i2 | CONTROL_END; // & AVAILABLE_BITS
                            indexTable[c2] = 0;
                        } else {
                            if (prev >= 0)
                                indexTable[prev] |= CONTROL_END; // (indexTable[prev] & AVAILABLE_BITS)
                            if (prev < 0 || (i & CONTROL_BITS) == CONTROL_EMPTY)
                                indexTable[curr] = 0;
                        }
                    }
                    if (j == firstEmptyIndex-1) {
                        firstEmptyIndex = j;
                    } else {
                        indexTable[k] = firstDeletedIndex < 0 ?
                            CONTROL_END : firstDeletedIndex;
                        firstDeletedIndex = j;
                    }
                    Object oldValue = index != NO_INDEX ? null :
                        keyIndexShift == 0 ? DUMMY_VALUE :
                        keyValueTable[(j<<keyIndexShift)+2];
                    keyValueTable[(j<<keyIndexShift)+1] = null;
                    if (keyIndexShift > 0)
                        keyValueTable[(j<<keyIndexShift)+2] = null;
                    modCount++;
                    if (this instanceof FastLinkedHashMap)
                        removeHook(j);
                    // validate("Remove "+key+", "+index);
                    return (V)oldValue;
                }
            }
            prev = curr;
            if ((i & CONTROL_BITS) == CONTROL_END)
                break; // END is more frequent - check it first
            else if ((i & CONTROL_BITS) == CONTROL_OVERFLOW)
                curr = k;
            else if ((i & CONTROL_BITS) == CONTROL_NEXT)
                curr = (curr+1) & (hashLen-1);
            else break;
            i = indexTable[curr];
        }
        return (V)NOT_FOUND;
    }

    /**
     * Removes all of the mappings from this map.
     * The map will be empty after this call returns.
     */
    public void clear() {
        if (indexTable != null)
            Arrays.fill(indexTable, 0, hashLen + firstEmptyIndex, 0);
        if (keyValueTable != null)
            Arrays.fill(keyValueTable, 0, (firstEmptyIndex<<keyIndexShift)+1, null);
        size = 0;
        firstEmptyIndex = 0;
        firstDeletedIndex = -1;
        modCount++;
        nullKeyPresent = false;
    }

    /**
     * Returns a shallow copy of this <tt>HashMap</tt> instance:
     * the keys and values themselves are not cloned.
     *
     * @return a shallow copy of this map
     */
    @SuppressWarnings("unchecked")
    public FastHashMap<K,V> clone() {
        FastHashMap<K,V> that = null;
        try {
            that = (FastHashMap<K,V>)super.clone();
        } catch (CloneNotSupportedException e) {
        }
        if (indexTable != null)
            that.indexTable = Arrays.copyOf(indexTable, hashLen+threshold);
        if (keyValueTable != null)
            that.keyValueTable = Arrays.copyOf(keyValueTable, (threshold<<keyIndexShift)+1);
        that.keySet = null;
        that.values = null;
        that.entrySet = null;
        that.modCount = 0;
        return that;
    }

    /**
     * Returns the number of key-value mappings in this map.
     *
     * @return the number of key-value mappings in this map
     */
    public int size() {
        return size;
    }

    /**
     * Returns <tt>true</tt> if this map contains no key-value mappings.
     *
     * @return <tt>true</tt> if this map contains no key-value mappings
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * Returns <tt>true</tt> if this map contains a mapping for the
     * specified key.
     *
     * @param   key   The key whose presence in this map is to be tested
     * @return <tt>true</tt> if this map contains a mapping for the specified
     * key.
     */
    public boolean containsKey(Object key) {
        return positionOf(key) != NO_INDEX;
    }

    /**
     * Copies all of the mappings from the specified map to this map.
     * These mappings will replace any mappings that this map had for
     * any of the keys currently in the specified map.
     *
     * @param m mappings to be stored in this map
     * @throws NullPointerException if the specified map is null
     */
    public void putAll(Map<? extends K, ? extends V> m) {
        int mSize = m.size();
        if (mSize == 0)
            return;
        if (mSize > threshold) {
            int newCapacity = hashLen;
            int newThreshold;
            do {
                newCapacity <<= 1;
                newThreshold = (int)(newCapacity * loadFactor);
            } while (newThreshold < mSize);
            resize(newCapacity);
        }
        if (m instanceof FastHashMap) {
            @SuppressWarnings("unchecked")
            FastHashMap<K,V> fm = (FastHashMap<K,V>)m;
            for (int i = fm.iterateFirst(); i != NO_INDEX; i = fm.iterateNext(i)) {
                @SuppressWarnings("unchecked")
                K key = (K)fm.keyValueTable[(i<<fm.keyIndexShift)+1];
                @SuppressWarnings("unchecked")
                V value = (V)(fm.keyIndexShift > 0 ?
                    fm.keyValueTable[(i<<fm.keyIndexShift)+2] :
                    DUMMY_VALUE);
                put(key, value);
            }
        } else {
            for (Map.Entry<? extends K, ? extends V> e : m.entrySet())
                put(e.getKey(), e.getValue());
        }
    }

    /**
     * Returns <tt>true</tt> if this map maps one or more keys to the
     * specified value.
     *
     * @param value value whose presence in this map is to be tested
     * @return <tt>true</tt> if this map maps one or more keys to the
     *         specified value
     */
    public boolean containsValue(Object value) {
        // Check arrays lazy allocation
        if (keyValueTable == null || size == 0)
            return false;
        // No values in table special case
        if (keyIndexShift == 0)
            return size > 0 && value == DUMMY_VALUE;
        // Search
        for (int i = NULL_INDEX; i < firstEmptyIndex ; i++)
            if (!isEmpty(i)) { // Not deleted
                Object o = keyValueTable[(i<<keyIndexShift)+2];
                if (o == value || o != null && o.equals(value))
                    return true;
            }
        return false;
    }

    /**
     * Each of these fields are initialized to contain an instance of the
     * appropriate view the first time this view is requested.  The views are
     * stateless, so there's no reason to create more than one of each.
     */
    private transient volatile Set<K> keySet = null;
    private transient volatile Collection<V> values = null;
    private transient volatile Set<Map.Entry<K,V>> entrySet = null;

    /**
     * Returns a {@link Set} view of the keys contained in this map.
     * The set is backed by the map, so changes to the map are
     * reflected in the set, and vice-versa.  If the map is modified
     * while an iteration over the set is in progress (except through
     * the iterator's own <tt>remove</tt> operation), the results of
     * the iteration are undefined.  The set supports element removal,
     * which removes the corresponding mapping from the map, via the
     * <tt>Iterator.remove</tt>, <tt>Set.remove</tt>,
     * <tt>removeAll</tt>, <tt>retainAll</tt>, and <tt>clear</tt>
     * operations.  It does not support the <tt>add</tt> or <tt>addAll</tt>
     * operations.
     */
    public Set<K> keySet() {
        Set<K> ks = keySet;
        return (ks != null ? ks : (keySet = new KeySet()));
    }

    /**
     * This method defines this map iteration order.
     * This order can be changed in subclasses like LinkedHashMap.
     *
     * @return  index of the first element.
     */
    int iterateFirst() {
        if (size == 0) return NO_INDEX;
        if (nullKeyPresent) return NULL_INDEX;
        int i = 0;
        while(isEmpty(i)) i++;
        return i;
    }

    /**
     * This method defines this map iteration order.
     * This order can be changed in subclasses like LinkedHashMap.
     *
     * @param  i  index if the current element.
     * @return  index of the next element.
     */
    int iterateNext(int i) {
        do i++; while (i < firstEmptyIndex && isEmpty(i));
        return i < firstEmptyIndex ? i : NO_INDEX;
    }

    /**
     * Generic iterator over this map.
     * value() method should return the real elements.
     */
    private abstract class HashIterator<E> implements Iterator<E> {
        boolean simpleOrder = !(FastHashMap.this instanceof FastLinkedHashMap);
        int nextIndex = iterateFirst();
        int lastIndex = NO_INDEX;
        int expectedModCount = modCount; // For fast-fail
        public final boolean hasNext() {
            return nextIndex != NO_INDEX && nextIndex < firstEmptyIndex;
        }
        public final E next() {
            if (modCount != expectedModCount)
                throw new ConcurrentModificationException();
            if (nextIndex == NO_INDEX || nextIndex >= firstEmptyIndex)
                throw new NoSuchElementException();
            lastIndex = nextIndex;
            if (simpleOrder)
                do nextIndex++;
                while (firstDeletedIndex >= 0 && nextIndex < firstEmptyIndex &&
                    keyValueTable[(nextIndex<<keyIndexShift)+1] == null);
            else
                nextIndex = iterateNext(nextIndex);
            return value();
        }
        public final void remove() {
            if (lastIndex == NO_INDEX)
                throw new IllegalStateException();
            if (modCount != expectedModCount)
                throw new ConcurrentModificationException();
            removeKey(lastIndex == NULL_INDEX ? null :
                keyValueTable[(lastIndex<<keyIndexShift)+1], lastIndex);
            lastIndex = NO_INDEX;
            expectedModCount = modCount;
        }
        abstract E value();
    }

    private final class KeyIterator extends HashIterator<K> {
        @SuppressWarnings("unchecked")
        K value() {
            return lastIndex == NULL_INDEX ? null :
                (K)keyValueTable[(lastIndex<<keyIndexShift)+1];
        }
    }

    private final class KeySet extends AbstractSet<K> {
        public Iterator<K> iterator() {
            return new KeyIterator();
        }
        public int size() {
            return size;
        }
        public boolean isEmpty() {
            return size == 0;
        }
        public boolean contains(Object o) {
            return containsKey(o);
        }
        public boolean remove(Object o) {
            return FastHashMap.this.removeKey(o, NO_INDEX) != NOT_FOUND;
        }
        public void clear() {
          FastHashMap.this.clear();
        }
    }

    /**
     * Returns a {@link Set} view of the mappings contained in this map.
     * The set is backed by the map, so changes to the map are
     * reflected in the set, and vice-versa.  If the map is modified
     * while an iteration over the set is in progress (except through
     * the iterator's own <tt>remove</tt> operation, or through the
     * <tt>setValue</tt> operation on a map entry returned by the
     * iterator) the results of the iteration are undefined.  The set
     * supports element removal, which removes the corresponding
     * mapping from the map, via the <tt>Iterator.remove</tt>,
     * <tt>Set.remove</tt>, <tt>removeAll</tt>, <tt>retainAll</tt> and
     * <tt>clear</tt> operations.  It does not support the
     * <tt>add</tt> or <tt>addAll</tt> operations.
     *
     * @return a set view of the mappings contained in this map
     */
    public Set<Map.Entry<K,V>> entrySet() {
        Set<Map.Entry<K,V>> es = entrySet;
        return es != null ? es : (entrySet = new EntrySet());
    }

    private final class EntryIterator extends HashIterator<Map.Entry<K,V>> {
        final Map.Entry<K,V> value() {
            return new Entry(lastIndex);
        }
    }

    private final class EntrySet extends AbstractSet<Map.Entry<K,V>> {
        public Iterator<Map.Entry<K,V>> iterator() {
            return new EntryIterator();
        }
        public boolean contains(Object o) {
            if (!(o instanceof Map.Entry))
                return false;
            @SuppressWarnings("unchecked")
            Map.Entry<K,V> e = (Map.Entry<K,V>) o;
            int i = positionOf(e.getKey());
            if (i == NO_INDEX) return false;
            // HashSet (keyIndexShift==0) uses only keySet
            Object v1 = keyValueTable[(i<<1)+2];
            Object v2 = e.getValue();
            return v1 == v2 || v1 != null && v1.equals(v2);
        }
        public boolean remove(Object o) {
            if (!(o instanceof Map.Entry))
                return false;
            @SuppressWarnings("unchecked")
            Map.Entry<K,V> e = (Map.Entry<K,V>) o;
            K key = e.getKey();
            int i = positionOf(key);
            if (i == NO_INDEX) return false;
            Object v1 = keyValueTable[(i<<1)+2];
            Object v2 = e.getValue();
            if (v1 != v2 && (v1 == null || !v1.equals(v2)))
                return false;
            removeKey(key, i);
            return true;
        }
        public int size() {
            return size;
        }
        public boolean isEmpty() {
            return size == 0;
        }
        public void clear() {
            FastHashMap.this.clear();
        }
    }

    /**
     * Returns a {@link Collection} view of the values contained in this map.
     * The collection is backed by the map, so changes to the map are
     * reflected in the collection, and vice-versa.  If the map is
     * modified while an iteration over the collection is in progress
     * (except through the iterator's own <tt>remove</tt> operation),
     * the results of the iteration are undefined.  The collection
     * supports element removal, which removes the corresponding
     * mapping from the map, via the <tt>Iterator.remove</tt>,
     * <tt>Collection.remove</tt>, <tt>removeAll</tt>,
     * <tt>retainAll</tt> and <tt>clear</tt> operations.  It does not
     * support the <tt>add</tt> or <tt>addAll</tt> operations.
     */
    public Collection<V> values() {
        Collection<V> vs = values;
        return (vs != null ? vs : (values = new Values()));
    }

    private final class ValueIterator extends HashIterator<V> {
        @SuppressWarnings("unchecked")
        V value() {
            // HashSet (keyIndexShift==0) uses only keySet
            return (V)keyValueTable[(lastIndex<<1)+2];
        }
    }

    private final class Values extends AbstractCollection<V> {
        public Iterator<V> iterator() {
            return new ValueIterator();
        }
        public int size() {
            return size;
        }
        public boolean isEmpty() {
            return size == 0;
        }
        public boolean contains(Object o) {
            return containsValue(o);
        }
        public void clear() {
            FastHashMap.this.clear();
        }
    }

    /**
     * Save the state of the <tt>HashMap</tt> instance
     * to a stream (i.e., serialize it).
     *
     * @serialData The <i>capacity</i> of the HashMap (the length of the
     *             bucket array) is emitted (int), followed by the
     *             <i>size</i> (an int, the number of key-value
     *             mappings), followed by the key (Object) and value (Object)
     *             for each key-value mapping.  The key-value mappings are
     *             emitted in no particular order.
     */
    private void writeObject(ObjectOutputStream s)
        throws IOException
    {
        // Write out the threshold, loadfactor, and any hidden stuff
        s.defaultWriteObject();

        // Write out number of buckets
        s.writeInt(hashLen);

        // Write out size (number of Mappings)
        s.writeInt(size);

        // Write out keys and values (alternating)
        for (int i = iterateFirst(); i != NO_INDEX; i = iterateNext(i)) {
            s.writeObject(i == NULL_INDEX ? null :
                keyValueTable[(i<<keyIndexShift)+1]);
            s.writeObject(keyIndexShift > 0 ?
                keyValueTable[(i<<keyIndexShift)+2] : null);
        }
    }

    private static final long serialVersionUID = 362498820763181265L;

    /**
     * Reconstitute the <tt>HashMap</tt> instance
     * from a stream (i.e., deserialize it).
     */
    private void readObject(ObjectInputStream s)
         throws IOException, ClassNotFoundException
    {
        // Read in the threshold, loadfactor, and any hidden stuff
        s.defaultReadObject();

        // Read in number of buckets and allocate the bucket array;
        hashLen = s.readInt();
        keyIndexShift = 1;
        keyValueTable = new Object[(threshold<<keyIndexShift)+1];
        indexTable = new int[hashLen+threshold];
        firstDeletedIndex = -1;

        init();  // Give subclass a chance to do its thing.

        // Read in size (number of Mappings)
        int size = s.readInt();

        // Read the keys and values, and put the mappings in the HashMap
        for (int i=0; i<size; i++) {
            @SuppressWarnings("unchecked")
            K key = (K) s.readObject();
            @SuppressWarnings("unchecked")
            V value = (V) s.readObject();
            put(key, value, false);
        }
    }

    final class Entry implements Map.Entry<K,V> {
        final int index;
        final K key;
        V value;
        @SuppressWarnings("unchecked")
        Entry(int index) {
            this.index = index;
            this.key = index == NULL_INDEX ? null :
                (K)keyValueTable[(index<<keyIndexShift)+1];
            this.value = (V)(keyIndexShift == 0 ? DUMMY_VALUE :
              keyValueTable[(index<<keyIndexShift)+2]);
        }
        public final K getKey() {
            return key;
        }
        @SuppressWarnings("unchecked")
        public final V getValue() {
            // HashSet (keyIndexShift == 0) does not use getValue
            if(index == NULL_INDEX ? nullKeyPresent :
                keyValueTable[(index<<1)+1] == key)
                value = (V)keyValueTable[(index<<1)+2];
            return value;
        }
        public final V setValue(V newValue) {
            // HashSet (keyIndexShift == 0) does not use setValue
            if(index == NULL_INDEX ? nullKeyPresent :
                keyValueTable[(index<<1)+1] == key) {
                @SuppressWarnings("unchecked")
                V oldValue = (V)keyValueTable[(index<<1)+2];
                keyValueTable[(index<<1)+2] = value = newValue;
                return oldValue;
            }
            V oldValue = value;
            value = newValue;
            return oldValue;
        }
        public boolean equals(Object o) {
            if (!(o instanceof Map.Entry))
                return false;
            @SuppressWarnings("unchecked")
            Map.Entry<K,V> that = (Map.Entry<K,V>)o;
            K key2 = that.getKey();
            if(key == key2 || (key != null && key.equals(key2))) {
                V value2 = that.getValue();
                return getValue() == value2 || (value != null && value.equals(value2));
            }
            return false;
        }
        public int hashCode() {
            return (key     == null ? 0 :   key.hashCode()) ^
                (getValue() == null ? 0 : value.hashCode());
        }
        public String toString() {
            return key + "=" + getValue();
        }
    }

    // These methods are used when serializing HashSets
    int   capacity()     { return hashLen; }
    float loadFactor()   { return loadFactor; }

    // These hooks are needed for LinkedHashMap
    void addHook(int i) { }
    void updateHook(int i) { }
    void removeHook(int i) { }
    void relocateHook(int newIndex, int oldIndex) { }

    /**
     * Internal self-test.
    void validate(String s) {
        int numberOfKeys = nullKeyPresent ? 1 : 0;
        for (int i = 0; i < hashLen; i++) {
            int index = indexTable[i];
            // Another hash bin?
            if (index != 0 && (index & CONTROL_BITS) == CONTROL_EMPTY) {
                int i2 = (i-1) & (hashLen-1);
                if ((indexTable[i2] & CONTROL_BITS) != CONTROL_NEXT)
                    throw new RuntimeException("Next at position "+i+" is incorrect");
            }
            if ((index & CONTROL_BITS) == CONTROL_EMPTY) continue;
            // Check first cell
            int cur = index & (hashLen-1);
            if (cur >= threshold)
                throw new RuntimeException("Bad index "+cur+" in hash bin "+i);
            Object key = keyValueTable[(cur<<keyIndexShift)+1];
            int mask = AVAILABLE_BITS ^ (hashLen-1);
            if (key == null)
                throw new RuntimeException("Null (empty) key in hash bin "+i+". "+s);
            int hc = hash(key.hashCode());
            if ((hc & (hashLen-1)) != i)
                throw new RuntimeException("Key "+key+" is in wrong hash basket ("+
                    i+") must be "+(hc & (hashLen-1))+". "+s);
            if ((hc & mask) != (index & mask))
                throw new RuntimeException("Key "+key+" has incorrect hashcode bits");
            numberOfKeys++;
            // Check next cell
            if ((index & CONTROL_BITS) == CONTROL_NEXT) {
                int i1 = (i+1) & (hashLen-1);
                int index1 = indexTable[i1];
                if (index1 == 0)
                    throw new RuntimeException("Next for "+i+" is 0. "+s);
                if ((index1 & CONTROL_BITS) != CONTROL_EMPTY)
                    throw new RuntimeException("Next for "+i+" has wrong control bits. "+s);
                key = keyValueTable[((index1 & (hashLen-1))<<keyIndexShift)+1];
                hc = hash(key.hashCode());
                if ((hc & (hashLen-1)) != i)
                    throw new RuntimeException("Next key "+key+" is in wrong hash basket ("+
                        i+") must be "+(hc & (hashLen-1)));
                if ((hc & mask) != (index1 & mask))
                    throw new RuntimeException("Next key "+key+" has incorrect hashcode bits");
                numberOfKeys++;
            }
            // Check overflow
            while ((index & CONTROL_BITS) == CONTROL_OVERFLOW) {
                index = indexTable[hashLen+cur];
                if ((index & CONTROL_BITS) == CONTROL_EMPTY)
                    throw new RuntimeException("Incorrect CONTROL_EMPTY in hash basket "+i+" overflow. "+s);
                if ((index & CONTROL_BITS) == CONTROL_NEXT)
                    throw new RuntimeException("Incorrect CONTROL_NEXT in hash basket "+i+" overflow. "+s);
                cur = index & (hashLen-1);
                key = keyValueTable[(cur<<keyIndexShift)+1];
                hc = hash(key.hashCode());
                if ((hc & (hashLen-1)) != i)
                    throw new RuntimeException("Overflow key "+key+" is in wrong hash basket ("+
                        i+") must be "+(hc & (hashLen-1)));
                if ((hc & mask) != (index & mask))
                    throw new RuntimeException("Overflow key "+key+" has incorrect hashcode bits");
                numberOfKeys++;
            }
        }
        if (numberOfKeys != size)
            throw new RuntimeException("Size("+size+") != # of keys("+numberOfKeys+")");
        int numberOfDeletedIndices = 0;
        int i = firstDeletedIndex;
        while (i >= 0) {
            numberOfDeletedIndices++;
            i = indexTable[hashLen+i];
            if (i == CONTROL_END) break;
            if (i >= threshold || i < 0)
                throw new RuntimeException("Incorrect entry in deleted list ("+i+")");
        }
        if (numberOfDeletedIndices != firstEmptyIndex - size + (nullKeyPresent ? 1 : 0))
            throw new RuntimeException("Deleted # ("+numberOfDeletedIndices+
                ") must be "+(firstEmptyIndex - size));
    }
     */

    /**
     * Returns the hash code value for this map.  The hash code of a map is
     * defined to be the sum of the hash codes of each entry in the map's
     * <tt>entrySet()</tt> view.  This ensures that <tt>m1.equals(m2)</tt>
     * implies that <tt>m1.hashCode()==m2.hashCode()</tt> for any two maps
     * <tt>m1</tt> and <tt>m2</tt>, as required by the general contract of
     * {@link Object#hashCode}.
     *
     * <p>This implementation iterates over <tt>entrySet()</tt>, calling
     * {@link Map.Entry#hashCode hashCode()} on each element (entry) in the
     * set, and adding up the results.
     *
     * @return the hash code value for this map
     * @see Map.Entry#hashCode()
     * @see Object#equals(Object)
     * @see Set#equals(Object)
     */
    public int hashCode() {
        int h = 0;
        for (int i = NULL_INDEX; i < firstEmptyIndex; i++)
            if (!isEmpty(i)) {
                int hc = i == NULL_INDEX ? 0 :
                    keyValueTable[(i<<keyIndexShift)+1].hashCode();
                Object value = keyIndexShift > 0 ?
                    keyValueTable[(i<<keyIndexShift)+2] :
                    DUMMY_VALUE;
                if (value != null) hc ^= value.hashCode();
                h += hc;
            }
        return h;
    }

    /**
     * Returns a string representation of this map.  The string representation
     * consists of a list of key-value mappings in the order returned by the
     * map's <tt>entrySet</tt> view's iterator, enclosed in braces
     * (<tt>"{}"</tt>).  Adjacent mappings are separated by the characters
     * <tt>", "</tt> (comma and space).  Each key-value mapping is rendered as
     * the key followed by an equals sign (<tt>"="</tt>) followed by the
     * associated value.  Keys and values are converted to strings as by
     * {@link String#valueOf(Object)}.
     *
     * @return a string representation of this map
     */
    public String toString() {
        if (size == 0)
            return "{}";
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        boolean first = true;
        for (int i = iterateFirst(); i != NO_INDEX; i = iterateNext(i)) {
            if (first)
                first = false;
            else
                sb.append(", ");
            Object key = i == NULL_INDEX ? null : keyValueTable[(i<<keyIndexShift)+1];
            Object value = keyIndexShift > 0 ?
                keyValueTable[(i<<keyIndexShift)+2] :
                DUMMY_VALUE;
            sb.append(key   == this ? "(this Map)" : key);
            sb.append('=');
            sb.append(value == this ? "(this Map)" : value);
        }
        return sb.append('}').toString();
    }

    /**
     * Compares the specified object with this map for equality.  Returns
     * <tt>true</tt> if the given object is also a map and the two maps
     * represent the same mappings.  More formally, two maps <tt>m1</tt> and
     * <tt>m2</tt> represent the same mappings if
     * <tt>m1.entrySet().equals(m2.entrySet())</tt>.  This ensures that the
     * <tt>equals</tt> method works properly across different implementations
     * of the <tt>Map</tt> interface.
     *
     * <p>This implementation first checks if the specified object is this map;
     * if so it returns <tt>true</tt>.  Then, it checks if the specified
     * object is a map whose size is identical to the size of this map; if
     * not, it returns <tt>false</tt>.  If so, it iterates over this map's
     * <tt>entrySet</tt> collection, and checks that the specified map
     * contains each mapping that this map contains.  If the specified map
     * fails to contain such a mapping, <tt>false</tt> is returned.  If the
     * iteration completes, <tt>true</tt> is returned.
     *
     * @param o object to be compared for equality with this map
     * @return <tt>true</tt> if the specified object is equal to this map
     */
    public boolean equals(Object o) {
        if (o == this)
            return true;
        if (!(o instanceof Map))
            return false;
        @SuppressWarnings("unchecked")
        Map<K,V> m = (Map<K,V>) o;
        if (m.size() != size)
            return false;
        for (int i = NULL_INDEX; i < firstEmptyIndex; i++)
            if (!isEmpty(i)) {
                Object key = i == NULL_INDEX ? null : keyValueTable[(i<<keyIndexShift)+1];
                Object value = keyIndexShift > 0 ?
                    keyValueTable[(i<<keyIndexShift)+2] :
                    DUMMY_VALUE;
                if (value == null) {
                    if (!(m.get(key) == null && m.containsKey(key)))
                        return false;
                } else {
                    Object value2 = m.get(key);
                    if (value != value2 && !value.equals(value2))
                        return false;
                }
            }
        return true;
    }
}
