package bufmgr;

import global.*;

import java.util.LinkedList;
import java.util.ListIterator;

class HashNode {
    PageId pageId;
    int frameIndex;

    public HashNode(PageId pageId, int frameIndex) {
        this.pageId = pageId;
        this.frameIndex = frameIndex;
    }
}

public class CustomHashTable {
    private LinkedList<HashNode>[] buckets;
    private int capacity;

    public CustomHashTable(int entries) {
        capacity = entries;
        buckets = new LinkedList[capacity];
        for (int i = 0; i < buckets.length; i++) {
            buckets[i] = new LinkedList<HashNode>();
        }
    }

    private int computeHash(PageId key) {
        int a = 7;
        int b = 3;
        int hash = Math.abs((a * key.pid + b) % capacity);
        return hash;
    }

    public void put(PageId key, int value) {
        int hash = computeHash(key);
        HashNode newNode = new HashNode(key, value);
        LinkedList<HashNode> bucket = buckets[hash];
        bucket.addFirst(newNode);
    }

    public Integer get(PageId key) {
        int hash = computeHash(key);
        LinkedList<HashNode> bucket = buckets[hash];
        for (HashNode node : bucket) {
            if (node.pageId.pid == key.pid) {
                return node.frameIndex;
            }
        }
        return null;
    }

    public void remove(PageId key) {
        int hash = computeHash(key);
        LinkedList<HashNode> bucket = buckets[hash];
        ListIterator<HashNode> iter = bucket.listIterator();
        while (iter.hasNext()) {
            HashNode node = iter.next();
            if (node.pageId.pid == key.pid) {
                iter.remove();
                return;
            }
        }
    }
}
