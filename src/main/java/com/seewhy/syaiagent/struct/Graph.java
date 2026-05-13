package com.seewhy.syaiagent.struct;

import java.util.*;

class Graph {
    private int vertices;
    private LinkedList<Integer>[] adjList;

    public Graph(int vertices) {
        this.vertices = vertices;
        adjList = new LinkedList[vertices];
        for (int i = 0; i < vertices; i++) {
            adjList[i] = new LinkedList<>();
        }
    }

    // 添加有向边
    public void addEdge(int src, int dest) {
        adjList[src].add(dest);
    }

    // 添加无向边
    public void addUndirectedEdge(int src, int dest) {
        adjList[src].add(dest);
        adjList[dest].add(src);
    }

    // 广度优先遍历
    public void bfs(int start) {
        boolean[] visited = new boolean[vertices];
        Queue<Integer> queue = new LinkedList<>();
        visited[start] = true;
        queue.offer(start);
        while (!queue.isEmpty()) {
            int node = queue.poll();
            System.out.print(node + " ");
            for (int neighbor : adjList[node]) {
                if (!visited[neighbor]) {
                    visited[neighbor] = true;
                    queue.offer(neighbor);
                }
            }
        }
    }

    // 深度优先遍历（递归）
    public void dfs(int start) {
        boolean[] visited = new boolean[vertices];
        dfsRec(start, visited);
    }
    private void dfsRec(int node, boolean[] visited) {
        visited[node] = true;
        System.out.print(node + " ");
        for (int neighbor : adjList[node]) {
            if (!visited[neighbor]) {
                dfsRec(neighbor, visited);
            }
        }
    }
}