package jbse.mem;

import java.util.Map;

import jbse.bc.Signature;
import jbse.val.Calculator;
import jbse.val.MemoryPath;

/**
 * Class that represent an instance of an object in the heap.
 */
public class Instance extends Objekt {
    /**
     * Constructor.
     * 
     * @param calc a {@link Calculator}.
     * @param className a {@code String}, the name of the class of 
     *        this {@link Instance} (e.g. {@code "java/lang/Object"}).
     * @param origin the origin of the {@code Instance}, if symbolic, 
     *        or {@code null}, if concrete.
     * @param epoch the creation {@link Epoch} of this {@link Instance}. 
     *        It can be null when
     *        {@code epoch == }{@link Epoch#EPOCH_AFTER_START}.
     * @param numOfStaticFields an {@code int}, the number of static fields.
     * @param fieldSignatures varargs of field {@link Signature}s, all the
     *        fields this instance knows.
     */
    protected Instance(Calculator calc, String className, MemoryPath origin, Epoch epoch, int numOfStaticFields, Signature... fieldSignatures) {
        super(calc, className, origin, epoch, false, numOfStaticFields, fieldSignatures);
    }
    
    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder();
        buf.append("[Class:");
        buf.append(this.type);
        buf.append(", Fields:{");
        boolean isFirst = true;
        for (Map.Entry<String, Variable> e : this.fields.entrySet()) {
            if (isFirst) {
                isFirst = false;
            } else {
                buf.append(", ");
            }
            buf.append(e.getValue().toString());
        }
        buf.append("}]");
        return buf.toString();
    }
    
    @Override
    public Instance clone() {
        final Instance o = (Instance) super.clone();
        o.fields = fieldsDeepCopy();
        
        return o;
    }
}