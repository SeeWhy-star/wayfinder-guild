package com.seewhy.syaiagent.struct;

class ArrayStack {
    private int[] data;
    private int top; // 栈顶指针

    public ArrayStack(int capacity) {
        data = new int[capacity];
        top = -1;
    }

    public void push(int val) {
        if (top == data.length - 1) {
            throw new IllegalStateException("Stack is full");
        }
        data[++top] = val;
    }

    public int pop() {
        if (isEmpty()) throw new IllegalStateException("Stack is empty");
        return data[top--];
    }

    public int peek() {
        if (isEmpty()) throw new IllegalStateException("Stack is empty");
        return data[top];
    }

    public boolean isEmpty() { return top == -1; }
    public int size() { return top + 1; }
}