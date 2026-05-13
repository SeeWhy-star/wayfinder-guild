package com.seewhy.syaiagent.struct;

public class Main {
    public static void main(String[] args) {
        // 测试链表
        SinglyLinkedList list = new SinglyLinkedList();
        list.addAtHead(1);
        list.addAtTail(3);
        list.addAtTail(5);
        list.delete(3);
        System.out.println("链表大小: " + list.size());

        // 测试栈
        ArrayStack stack = new ArrayStack(5);
        stack.push(10);
        stack.push(20);
        System.out.println("栈顶: " + stack.peek());
        System.out.println("出栈: " + stack.pop());

        // 测试队列
        CircularQueue queue = new CircularQueue(3);
        queue.enqueue(1);
        queue.enqueue(2);
        System.out.println("出队: " + queue.dequeue());
        System.out.println("队首: " + queue.peek());

        // 测试二叉搜索树
        BinarySearchTree bst = new BinarySearchTree();
        bst.insert(5);
        bst.insert(3);
        bst.insert(7);
        System.out.println("查找 3: " + bst.search(3));

        // 测试最小堆
        MinHeap heap = new MinHeap(10);
        heap.insert(3);
        heap.insert(1);
        heap.insert(6);
        System.out.println("堆最小值: " + heap.extractMin());

        // 测试图
        Graph graph = new Graph(4);
        graph.addUndirectedEdge(0, 1);
        graph.addUndirectedEdge(0, 2);
        graph.addUndirectedEdge(1, 3);
        System.out.print("BFS: ");
        graph.bfs(0);
        System.out.print("\nDFS: ");
        graph.dfs(0);
    }
}