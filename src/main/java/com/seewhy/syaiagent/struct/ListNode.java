package com.seewhy.syaiagent.struct;

class ListNode {
    int val;
    ListNode next;
    ListNode(int val) { this.val = val; }
}

class SinglyLinkedList {
    private ListNode head;
    private int size;

    // 在头部插入
    public void addAtHead(int val) {
        ListNode newNode = new ListNode(val);
        newNode.next = head;
        head = newNode;
        size++;
    }

    // 在尾部插入
    public void addAtTail(int val) {
        if (head == null) {
            addAtHead(val);
            return;
        }
        ListNode cur = head;
        while (cur.next != null) cur = cur.next;
        cur.next = new ListNode(val);
        size++;
    }

    // 删除第一个值为 val 的节点
    public void delete(int val) {
        if (head == null) return;
        if (head.val == val) {
            head = head.next;
            size--;
            return;
        }
        ListNode cur = head;
        while (cur.next != null && cur.next.val != val) cur = cur.next;
        if (cur.next != null) {
            cur.next = cur.next.next;
            size--;
        }
    }

    public int size() { return size; }
    public boolean isEmpty() { return size == 0; }
}