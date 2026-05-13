package com.seewhy.syaiagent.struct;

class TreeNode {
    int val;
    TreeNode left, right;
    TreeNode(int val) { this.val = val; }
}

class BinarySearchTree {
    private TreeNode root;

    public void insert(int val) {
        root = insertRec(root, val);
    }

    private TreeNode insertRec(TreeNode root, int val) {
        if (root == null) return new TreeNode(val);
        if (val < root.val) root.left = insertRec(root.left, val);
        else if (val > root.val) root.right = insertRec(root.right, val);
        return root;
    }

    public boolean search(int val) {
        return searchRec(root, val);
    }

    private boolean searchRec(TreeNode root, int val) {
        if (root == null) return false;
        if (val == root.val) return true;
        return val < root.val ? searchRec(root.left, val) : searchRec(root.right, val);
    }

    public void delete(int val) {
        root = deleteRec(root, val);
    }

    private TreeNode deleteRec(TreeNode root, int val) {
        if (root == null) return null;
        if (val < root.val) root.left = deleteRec(root.left, val);
        else if (val > root.val) root.right = deleteRec(root.right, val);
        else {
            // 节点有一个子节点或无子节点
            if (root.left == null) return root.right;
            if (root.right == null) return root.left;
            // 节点有两个子节点：用右子树的最小值替代当前节点
            root.val = minValue(root.right);
            root.right = deleteRec(root.right, root.val);
        }
        return root;
    }

    private int minValue(TreeNode root) {
        while (root.left != null) root = root.left;
        return root.val;
    }
}