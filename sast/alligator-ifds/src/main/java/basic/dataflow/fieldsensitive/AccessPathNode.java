package basic.dataflow.fieldsensitive;

import soot.SootField;
import soot.Value;

import java.util.LinkedList;
import java.util.List;

public class AccessPathNode implements Cloneable{
    public SootField field;
    public List<Value> values = new LinkedList<>(); // 对应这个field的所有value

    public List<AccessPathNode> children = new LinkedList<>();
    public AccessPathNode parent;

    public boolean isTaint = false;


    // 从AliasGraph里建立
    protected AccessPathNode(SootField field, Value value) {
        this.field = field;
        this.values.add(value);
    }

    public void addChild(AccessPathNode child) {
        this.children.add(child);
        child.parent = this;
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        AccessPathNode accessPathNode = new AccessPathNode(this.field, null);
        accessPathNode.field = this.field;
        accessPathNode.values = new LinkedList<>();
        accessPathNode.children = new LinkedList<>();
        accessPathNode.parent = null;
        accessPathNode.isTaint = this.isTaint;
        return accessPathNode;
    }

    // 递归的clone所有children
    public AccessPathNode recurseClone() throws CloneNotSupportedException {
        List<AccessPathNode> newChildren = new LinkedList<>();
        AccessPathNode thisClone = (AccessPathNode) this.clone();
        for(AccessPathNode child : this.children) {
            AccessPathNode childClone = child.recurseClone();
            newChildren.add(childClone);
        }

        // 加在thisClone里
        thisClone.children = new LinkedList<>();
        for(AccessPathNode child : newChildren) {
            thisClone.addChild(child);
        }
        return thisClone;
    }

    // 获得某个节点和他所有子孙组成的子树,需要深copy
    public AccessPathNode cloneSubTree() throws CloneNotSupportedException {
        // 现在clone出来的子树里的value都是空的，因为在新的上下文中，还没有哪个local value被指向field
        return this.recurseClone();
    }
}
