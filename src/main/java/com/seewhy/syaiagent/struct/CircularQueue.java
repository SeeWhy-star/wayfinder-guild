package com.seewhy.syaiagent.struct;

class CircularQueue {
    private int[] data;
    private int front; // 队头指针
    private int rear;  // 队尾指针
    private int size;

    public CircularQueue(int capacity) {
        data = new int[capacity];
        front = 0;
        rear = -1;
        size = 0;
    }

    public boolean enqueue(int val) {
        if (isFull()) return false;
        rear = (rear + 1) % data.length;
        data[rear] = val;
        size++;
        return true;
    }

    public int dequeue() {
        if (isEmpty()) throw new IllegalStateException("Queue is empty");
        int val = data[front];
        front = (front + 1) % data.length;
        size--;
        return val;
    }

    public int peek() {
        if (isEmpty()) throw new IllegalStateException("Queue is empty");
        return data[front];
    }

    public boolean isEmpty() { return size == 0; }
    public boolean isFull() { return size == data.length; }
    public int size() { return size; }
}