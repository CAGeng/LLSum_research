package basic.dataflow.fieldsensitive;

import soot.SootField;
import soot.Value;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

public class AliasGraph {
    public List<AccessPathNode> roots = new LinkedList<>();

    private HashMap<Value, AccessPathNode> value2Node = new HashMap<>();

    public AliasGraph() {
    }

    public AccessPathNode getNode(Value value) {
        if(value2Node.containsKey(value)) {
            return value2Node.get(value);
        }
        return null;
    }

    /**
     * get(a->data), 可能data不是一个具体的value，但作为field存在于graph中
     * 例子：最下面的data
     *                               b
     *                            |    \
     *                         a(field) c(data)
     *                            |
     *                          data
     */
    public AccessPathNode getNode(Value value, SootField field) {
        if(value2Node.containsKey(value)) {
            AccessPathNode valueNode = value2Node.get(value);
            for(AccessPathNode childNode : valueNode.children) {
                if(childNode.field == null) continue;
                if(childNode.field.equals(field)) {
                    return childNode;
                }
            }
        }
        return null;
    }

    public AccessPathNode createNode(SootField field, Value value) {
        AccessPathNode accessPathNode = new AccessPathNode(field, value);
        if(value != null){
            value2Node.put(value, accessPathNode);
            accessPathNode.values.add(value);
        }
        return accessPathNode;
    }

    public void addPointTo(Value value, AccessPathNode accessPathNode) {
        value2Node.put(value, accessPathNode);
        accessPathNode.values.add(value);
    }

    public void addRoot(AccessPathNode root) {
        roots.add(root);
        for(Value value : root.values) {
            value2Node.put(value, root);
        }
    }

    // ------------------------ 图操作 ------------------------

    public List<AccessPathNode> getPathFromRoot(AccessPathNode node) {
        List<AccessPathNode> path = new LinkedList<>();
        for(AccessPathNode cur = node; cur.parent != null; cur = cur.parent) {
            path.add(cur);
        }
        // 翻转顺序
        List<AccessPathNode> ret = new LinkedList<>();
        for(int i = path.size() - 1; i >= 0; i--) {
            ret.add(path.get(i));
        }
        return ret;
    }

    public void printGraph() {
        // dfs遍历graph
        for(AccessPathNode root : roots) {
            dfsPrint(root, 0);
        }
    }

    public void printNode(AccessPathNode node) {
        dfsPrint(node, 0);
    }

    private void dfsPrint(AccessPathNode node, int depth) {
        for(int i = 0; i < depth; i++) {
            System.out.print("  ");
        }
        System.out.print(node.field + "(" + node.values.size() + ") ");
//        for(Value value : node.values) {
//            System.out.print(value + " ");
//        }
//        System.out.println();
        for(AccessPathNode child : node.children) {
            System.out.print("\n");
            dfsPrint(child, depth + 1);
        }
        if(depth == 0) System.out.println();
    }

    public List<String> getAccessPaths(AccessPathNode root) {
        List<String> records = new LinkedList<>();
        findAllAccessPaths_recurse(root, "", records);
        return records;
    }

    private void findAllAccessPaths_recurse(AccessPathNode node, String currentPath, List<String> records) {
        if(node.field == null) {
            currentPath = "[root]";
        } else {
            currentPath += "->" + node.field;
        }
        records.add(currentPath);
        // 递归遍历子节点
        for (AccessPathNode child : node.children) {
            findAllTaintedAccessPaths_recurse(child, currentPath, records);
        }
    }

    public List<String> getAllTaintedAccessPaths() {
        List<String> allRecords = new LinkedList<>();
        for(AccessPathNode root : roots) {
            allRecords.addAll(getTaintedAccessPaths(root));
        }
        return allRecords;
    }

    public List<String> getTaintedAccessPaths(AccessPathNode root) {
        List<String> records = new LinkedList<>();
        findAllTaintedAccessPaths_recurse(root, "", records);
        return records;
    }

    private void findAllTaintedAccessPaths_recurse(AccessPathNode node, String currentPath, List<String> records) {
        if(node.field == null) {
            currentPath = "[root]";
        } else {
//            currentPath += "->" + node.field;
            currentPath += "->" + node.field.getName();
        }
        if(node.isTaint)
            records.add(currentPath);
        // 递归遍历子节点
        for (AccessPathNode child : node.children) {
            findAllTaintedAccessPaths_recurse(child, currentPath, records);
        }
    }

    public int treeSize(AccessPathNode node) {
        int count = 1; // self
        for(AccessPathNode child : node.children) {
            count += treeSize(child);
        }
        return count;
    }

    public int graphSize() {
        int count = 0;
        for(AccessPathNode root : roots) {
            count += treeSize(root);
        }
        return count;
    }

    public int treeSize_tainted(AccessPathNode node) {
        int count = node.isTaint ? 1 : 0; // self
        for(AccessPathNode child : node.children) {
            count += treeSize(child);
        }
        return count;
    }

    public int graphSize_tainted() {
        int count = 0;
        for(AccessPathNode root : roots) {
            count += treeSize_tainted(root);
        }
        return count;
    }


}
