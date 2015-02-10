package jbse.dec;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;

import jbse.bc.ClassHierarchy;
import jbse.bc.exc.ClassFileNotFoundException;
import jbse.common.Type;
import jbse.common.exc.UnexpectedInternalException;
import jbse.dec.exc.DecisionException;
import jbse.dec.exc.InvalidInputException;
import jbse.mem.Array;
import jbse.mem.Clause;
import jbse.mem.ClauseAssumeExpands;
import jbse.mem.Objekt;
import jbse.mem.State;
import jbse.mem.SwitchTable;
import jbse.mem.Util;
import jbse.tree.DecisionAlternative_XALOAD;
import jbse.tree.DecisionAlternative_XALOAD_Out;
import jbse.tree.DecisionAlternative_XALOAD_Resolved;
import jbse.tree.DecisionAlternative_XASTORE;
import jbse.tree.DecisionAlternative_XCMPY;
import jbse.tree.DecisionAlternative_XCMPY.Values;
import jbse.tree.DecisionAlternative_IFX;
import jbse.tree.DecisionAlternative_IFX_False;
import jbse.tree.DecisionAlternative_IFX_True;
import jbse.tree.DecisionAlternative_XLOAD_GETX;
import jbse.tree.DecisionAlternative_XLOAD_GETX_Resolved;
import jbse.tree.DecisionAlternative_XNEWARRAY;
import jbse.tree.DecisionAlternative_XNEWARRAY_Ok;
import jbse.tree.DecisionAlternative_XNEWARRAY_Wrong;
import jbse.tree.DecisionAlternative_XSWITCH;
import jbse.val.Any;
import jbse.val.Calculator;
import jbse.val.Expression;
import jbse.val.Operator;
import jbse.val.Primitive;
import jbse.val.ReferenceSymbolic;
import jbse.val.Simplex;
import jbse.val.Value;
import jbse.val.exc.InvalidOperandException;
import jbse.val.exc.InvalidTypeException;


/**
 * A {@link DecisionProcedureAlgorithms} decorates a {@link DecisionProcedure} 
 * by adding to it a number of higher-level methods which ease the calculations 
 * of {@link DecisionAlternative}s.
 * 
 * @author Pietro Braione
 */
public class DecisionProcedureAlgorithms extends DecisionProcedureDecorator {
	/**
	 * Enum for the possible (info) outcomes of a decision.
	 * 
	 * @author Pietro Braione
	 */
	public enum Outcome {
		/** Should refine, branching decision. */
		TT(true, true), 
		
		/** Should refine, nonbranching decision. */
		TF(true, false), 
		
		/** Should not refine, branching decision. */
		FT(false, true),
		
		/** Should not refine, nonbranching decision. */
		FF(false, false),
		
		/** Should refine, no reference expansion, branching decision. */
		TTT(true, true, true),
		
		/** Should refine, no reference expansion, nonbranching decision. */
		TTF(true, true, false),
		
		/** Should refine, reference expansion, branching decision. */
		TFT(true, false, true),
		
		/** Should refine, reference expansion, nonbranching decision. */
		TFF(true, false, false),
		
		/** Should not refine, no reference expansion, branching decision. */
		FTT(false, true, true),
		
		/** Should not refine, no reference expansion, nonbranching decision. */
		FTF(false, true, false),
		
		/** Should not refine, reference expansion, branching decision. */
		FFT(false, false, true),
		
		/** Should not refine, reference expansion, nonbranching decision. */
		FFF(false, false, false);

		private final boolean shouldRefine;
		private final boolean noReferenceExpansion;
		private final boolean branchingDecision;
		
		private Outcome(boolean shouldRefine, boolean isBranching) {
			this.shouldRefine = shouldRefine;
			this.noReferenceExpansion = false;
			this.branchingDecision = isBranching;
		}

		private Outcome(boolean shouldRefine, boolean noReferenceExpansion, boolean isBranching) {
			this.shouldRefine = shouldRefine;
			this.noReferenceExpansion = noReferenceExpansion;
			this.branchingDecision = isBranching;
		}
		
		/**
		 * Returns an outcome (reference resolution does not apply).
		 * 
		 * @param shouldRefine {@code true} iff should refine.
		 * @param branching {@code true} iff the decision is a 
		 *        branching one. 
		 * @return a suitable outcome,  
		 *         encapsulating the values of {@code shouldRefine}
		 *         and {@code branching}.
		 */
		public static Outcome val(boolean shouldRefine, boolean branching) {
			if (shouldRefine && branching) {
				return TT;
			} else if (shouldRefine && !branching) {
				return TF;
			} else if (!shouldRefine && branching) {
				return FT;
			} else { //if (!shouldRefine && !branching)
				return FF;
			}
		}
		
		/**
		 * Returns an outcome (reference resolution applies).
		 * 
		 * @param shouldRefine {@code true} iff should refine.
		 * @param noReferenceExpansion {@code true} iff 
		 *        the reference has not been
		 *        expanded (it is partially resolved).
		 * @param branching {@code true} iff the decision is a 
		 *        branching one. 
		 * @return a suitable outcome,  
		 *         encapsulating the values of {@code shouldRefine},
		 *         {@code partialReferenceResolution} and {@code branching}.
		 */
		public static Outcome val(boolean shouldRefine, boolean noReferenceExpansion, boolean branching) {
			if (shouldRefine && noReferenceExpansion && branching) {
				return TTT;
			} else if (shouldRefine && noReferenceExpansion && !branching) {
				return TTF;
			} else if (shouldRefine && !noReferenceExpansion && branching) {
				return TFT;
			} else if (shouldRefine && !noReferenceExpansion && !branching) {
				return TFF;
			} else if (!shouldRefine && noReferenceExpansion && branching) {
				return FTT;
			} else if (!shouldRefine && noReferenceExpansion && !branching) {
				return FTF;
			} else if (!shouldRefine && !noReferenceExpansion && branching) {
				return FFT;
			} else { //if (!shouldRefine && !partialReferenceResolution && !branching)
				return FFF;
			}
		}
		
		/**
		 * Should the generated states be refined?
		 * 
		 * @return {@code true} iff the generated states must be refined;
		 *         this happens when there is more than one outcome, or
		 *         some symbolic reference has been expanded.
		 */
		public boolean shouldRefine() {
			return this.shouldRefine;
		}

		/**
		 * Has the reference been expanded (is a reference resolution partial)?
		 * 
		 * @return {@code true} iff a reference resolution is suspect because
		 *         <em>partial</em>, i.e., because the reference is not resolved
		 *         by expansion. This happens when no concrete 
		 *         class is compatible with a symbolic reference's static type according to
		 *         the symbolic execution's constraints (which is an indicator of
		 *         badly specified constraints), or because the constraints forbid  
		 *         the reference to be expanded (which might be the consequence of a 
		 *         representation invariant of the data structure).
		 */
		public boolean noReferenceExpansion() {
			if (this == TT || this == TF || this == FT || this == FF) {
				throw new UnexpectedInternalException(this.toString() + " does not refer to reference expansion."); //TODO throw a better exception
			}
			return this.noReferenceExpansion;
		}
		
		/**
		 * Was the decision a branching one? 
		 * 
		 * @return {@code true} iff the decision taken 
		 *         was <em>branching</em>, i.e., it has
		 *         not a single, fixed outcome.
		 */
		public boolean branchingDecision() {
			return this.branchingDecision;
		}
	}
	
	private final Calculator calc;
	
	public DecisionProcedureAlgorithms(DecisionProcedure component, Calculator calc) {
		super(component);
		this.calc = calc;
	}

	/**
	 * Decides a condition for "branch if integer comparison" bytecodes.
	 * 
	 * @param condition a {@link Primitive} representing a logical value or clause.
	 * @param result a {@link SortedSet}{@code <}{@link DecisionAlternative_IFX}{@code >}, 
	 *            where the method will put a {@link DecisionAlternative_IFX_True} object
	 *            iff {@code condition} does not contradict the current assumptions, and 
	 *            a {@link DecisionAlternative_IFX_False} object iff
	 *            {@code condition.}{@link Primitive#not() not()} is an {@link Expression} that
	 *            does not contradict the current assumptions. Note that the two situations
	 *            are not mutually exclusive (they are if {@code condition} is concrete).
	 * @return an {@link Outcome}.
	 * @throws InvalidInputException when one of the parameters is incorrect.
	 * @throws DecisionException upon failure.
	 */
	//TODO should be final?
	public Outcome decideIf(Primitive condition, SortedSet<DecisionAlternative_IFX> result)
	throws InvalidInputException, DecisionException {
		if (condition == null || result == null) {
			throw new InvalidInputException("decideIf invoked with a null parameter");
		}
		if (condition.getType() != Type.BOOLEAN) {
			throw new InvalidInputException("condition has type " + condition.getType());
		}
		if (condition instanceof Simplex) {
			decideIfConcrete((Simplex) condition, result);
			return Outcome.val(false, false);
		} else {		
			final Outcome o = decideIfNonconcrete(condition, result);
			return o;
		}
	}
	
	private void decideIfConcrete(Simplex condition, SortedSet<DecisionAlternative_IFX> result) {
		final boolean conditionBoolean = (Boolean) condition.getActualValue();
		result.add(DecisionAlternative_IFX.toConcrete(conditionBoolean));
	}

	protected Outcome decideIfNonconcrete(Primitive condition, SortedSet<DecisionAlternative_IFX> result) 
	throws DecisionException {	
		final boolean shouldRefine;
		final DecisionAlternative_IFX T = DecisionAlternative_IFX.toNonconcrete(true);
		final DecisionAlternative_IFX F = DecisionAlternative_IFX.toNonconcrete(false);

		if (condition instanceof Any) {
			result.add(T);
			result.add(F);
			shouldRefine = false; //"don't care" does not require refinement
		} else {
			//TODO what if condition is neither Simplex, nor Any, nor Expression (i.e., FunctionApplication, Widening/NarrowingConversion, PrimitiveSymbolic, Term)?
			try {
				final Expression exp = (Expression) condition; 
				//this implementation saves one sat check in 50% cases
				//(it exploits the fact that if exp is unsat 
				//exp.not() is valid)
				if (this.isSat(exp)) {
					result.add(T);
					final Expression expNot = (Expression) condition.not(); 
					if (this.isSat(expNot)) {
						result.add(F);
					}
				} else {
					//exp is unsat, thus its negation is valid
					result.add(F);
				}
				shouldRefine = (result.size() > 1);
			} catch (InvalidTypeException e) {
				//this should never happen as arguments have been checked by the caller
				throw new UnexpectedInternalException(e);
			}
		}
		return Outcome.val(shouldRefine, true);
	}

	/**
	 * Decides a comparison for comparison bytecodes.
	 * 
	 * @param val1 a {@link Primitive}.
	 * @param val2 another {@link Primitive}.
	 * @param result a {@link SortedSet}{@code <}{@link DecisionAlternative_XCMPY}{@code >}, 
	 *            which the method will update by adding to it a {@link DecisionAlternative_XCMPY_Gt} object 
	 *            (respectively, {@link DecisionAlternative_XCMPY_Eq}, {@link DecisionAlternative_XCMPY_Lt})
	 *            iff {@code val1.gt(val2)} (respectively, {@code val1.eq(val2)}, 
	 *            {@code val1.lt(val2)}) does not contradict the current assumptions. 
	 *            Note that the three conditions are not mutually exclusive (they are when {@code val1} and 
	 *            {@code val2} are concrete).
	 * @return an {@link Outcome}.
	 * @throws InvalidInputException when one of the parameters is incorrect.
	 * @throws DecisionException upon failure.
	 */
	//TODO should be final?
	public Outcome decideComparison(Primitive val1, Primitive val2, SortedSet<DecisionAlternative_XCMPY> result)
	throws InvalidInputException, DecisionException {
		if (val1 == null || val2 == null || result == null) {
			throw new InvalidInputException("decideComparison invoked with a null parameter");
		}
		try {
			Operator.typeCheck(Operator.EQ, val1.getType(), val2.getType());
		} catch (InvalidTypeException e) {
			throw new InvalidInputException("decideComparison invoked with noncomparable parameters");
		}
		if ((val1 instanceof Simplex) && (val2 instanceof Simplex)) {
			decideComparisonConcrete((Simplex) val1, (Simplex) val2, result);
			return Outcome.val(false, false);
		} else {
			final Outcome o = decideComparisonNonconcrete(val1, val2, result);
			return o;
		}
	}
	
	private void decideComparisonConcrete(Simplex val1, Simplex val2, SortedSet<DecisionAlternative_XCMPY> result) {
		try {
			final Simplex conditionGt = (Simplex) val1.gt(val2);
			final boolean conditionGtValue = (Boolean) conditionGt.getActualValue();
			if (conditionGtValue) {
				result.add(DecisionAlternative_XCMPY.toConcrete(Values.GT));
			} else {
				final Simplex conditionEq = (Simplex) val1.eq(val2);
				final boolean conditionEqValue = (Boolean) conditionEq.getActualValue();
				if (conditionEqValue) {
					result.add(DecisionAlternative_XCMPY.toConcrete(Values.EQ));
				} else {
					result.add(DecisionAlternative_XCMPY.toConcrete(Values.LT));
				}
			}
		} catch (InvalidTypeException | InvalidOperandException e) {
			//this should never happen as arguments have been checked by the caller
			throw new UnexpectedInternalException(e);
		}
	}

	protected Outcome decideComparisonNonconcrete(Primitive val1, Primitive val2,
	SortedSet<DecisionAlternative_XCMPY> result) 
	throws DecisionException {
		final boolean shouldRefine;
		final DecisionAlternative_XCMPY GT = DecisionAlternative_XCMPY.toNonconcrete(Values.GT);
		final DecisionAlternative_XCMPY EQ = DecisionAlternative_XCMPY.toNonconcrete(Values.EQ);
		final DecisionAlternative_XCMPY LT = DecisionAlternative_XCMPY.toNonconcrete(Values.LT);
		
		if ((val1 instanceof Any) || (val2 instanceof Any)) {
			//1 - condition involving "don't care" values
			result.add(GT);
			result.add(EQ);
			result.add(LT);
			shouldRefine = false;
		} else {
			final Expression expGT;
			final Expression expEQ;
			final Expression expLT;
			try {
				expGT = (Expression) val1.gt(val2);
				expEQ = (Expression) val1.eq(val2);
				expLT = (Expression) val1.lt(val2);
			} catch (InvalidTypeException | InvalidOperandException e) {
				//this should never happen as arguments have been checked by the caller
				throw new UnexpectedInternalException(e);
			}

			//this implementation saves one sat check in 33% cases
			//(it exploits the fact that if both val1 > val2 and 
			//val1 = val2 are unsat, then val1 < val2 is valid)
			if (this.isSat(expGT)) {
				result.add(GT);
				if (this.isSat(expEQ)) {
					result.add(EQ);
				}
				if (this.isSat(expLT)) {
					result.add(LT); 
				}
			} else if (this.isSat(expEQ)) { //expGT is unsat, so either expEQ or expLT, or both, are SAT 
				result.add(EQ);
				if (this.isSat(expLT)) {
					result.add(LT); 
				}
			} else {
				//both expGT and expEQ are unsat; so expLT is valid
				result.add(LT);
			}
			shouldRefine = (result.size() > 1);
		}
		return Outcome.val(shouldRefine, true);
	}

	/**
	 * Decides a table or a range for switch bytecodes.
	 * 
	 * @param selector a {@link Primitive} with type int. 
	 * @param tab a {@link SwitchTable}.
	 * @param result a {@link SortedSet}{@code <}{@link DecisionAlternative_XSWITCH}{@code >} 
	 *            where the method will put the {@link DecisionAlternative_XSWITCH} objects {@code s} 
	 *            such that the equality of {@code selector.}{@link Primitive#eq(Primitive) eq}{@code (s.}{@link DecisionAlternative_XSWITCH#value() value()}{@code )}
	 *            does not contradict the current assumptions.
	 * @return an {@link Outcome}.
	 * @throws InvalidInputException when one of the parameters is incorrect.
	 * @throws DecisionException upon failure.
	 */
	//TODO should be final?
	public Outcome decideSwitch(Primitive selector, SwitchTable tab, SortedSet<DecisionAlternative_XSWITCH> result)
	throws InvalidInputException, DecisionException {
		if (selector == null || tab == null || result == null) {
			throw new InvalidInputException("decideSwitch invoked with a null parameter");
		}
		if (selector.getType() != Type.INT) {
			throw new InvalidInputException("switch selector has type " + selector.getType());
		}
		if (selector instanceof Simplex) {
			decideSwitchConcrete((Simplex) selector, tab, result);
			return Outcome.val(false, false);
		} else {
			final Outcome o = decideSwitchNonconcrete(selector, tab, result);
			return o;
		}
	}
	
	private void decideSwitchConcrete(Simplex selector, SwitchTable tab, SortedSet<DecisionAlternative_XSWITCH> result) {
		final int opValue = (Integer) selector.getActualValue();
		int branchId = 1;
		for (int i : tab) {
			if (i == opValue) { 
				result.add(DecisionAlternative_XSWITCH.toConcrete(i, branchId));
				return;
			}
			++branchId;
		}
		//not found
		result.add(DecisionAlternative_XSWITCH.toConcreteDefault(branchId));
	}

	/**
	 * Implements the portion of {@link #decideSwitch} which
	 * must query the component {@link DecisionProcedure}.
	 * @throws DecisionException
	 * 
	 * @see {@link #decideSwitch}.
	 */
	protected Outcome decideSwitchNonconcrete(Primitive selector, SwitchTable tab, SortedSet<DecisionAlternative_XSWITCH> result) 
	throws DecisionException {
		final boolean shouldRefine;

		final boolean isAny = (selector instanceof Any);
		int branchId = 1;
		try {
			for (int i : tab) {
				final Expression exp = (isAny ? null : (Expression) selector.eq(this.calc.valInt(i)));
				if (isAny || isSat(exp)) { 
					result.add(DecisionAlternative_XSWITCH.toNonconcrete(i, branchId));
				}
				++branchId;
			}
			if (isAny || isSat(tab.getDefaultClause(selector))) { 
				result.add(DecisionAlternative_XSWITCH.toNonconcreteDefault(branchId));
			}
			shouldRefine = (!isAny && (result.size() > 1));
			return Outcome.val(shouldRefine, true);
		} catch (InvalidOperandException | InvalidTypeException e) {
			//this should never happen as arguments have been checked by the caller
			throw new UnexpectedInternalException(e);
		}

	}

	/**
	 * Decides array creation.
	 * 
	 * @param countsNonNegative a {@link Primitive} expressing the fact that the count 
	 *        values popped from the operand stack are nonnegative.
	 * @param result a {@link SortedSet}{@code <}{@link DecisionAlternative_XNEWARRAY}{@code >}, which the method 
	 *            will update by adding to it a {@link DecisionAlternative_XNEWARRAY_Ok} 
	 *            (respectively, a {@link DecisionAlternative_XNEWARRAY_Wrong}) in the case
	 *            a successful (respectively, unsuccessful) creation of the array with
	 *            the provided count values does not contradict the current assumptions.
	 *            Note that the two situations are not mutually exclusive.
	 * @return an {@link Outcome}.
	 * @throws InvalidInputException when one of the parameters is incorrect.
	 * @throws DecisionException upon failure.
	 */
	//TODO should be final?
	public Outcome decideNewarray(Primitive countsNonNegative, SortedSet<DecisionAlternative_XNEWARRAY> result) 
	throws InvalidInputException, DecisionException {
		if (countsNonNegative == null || result == null) {
			throw new InvalidInputException("decideNewarray invoked with a null parameter");
		}
		if (countsNonNegative.getType() != Type.BOOLEAN) {
			throw new InvalidInputException("countsNonNegative type is " + countsNonNegative.getType());
		}
		if (countsNonNegative instanceof Simplex) {
			decideNewarrayConcrete((Simplex) countsNonNegative, result);
			return Outcome.val(false, false);
		} else {
			final Outcome o = decideNewarrayNonconcrete(countsNonNegative, result);
			return o;
		}
	}
	
	private void decideNewarrayConcrete(Simplex countsNonNegative, SortedSet<DecisionAlternative_XNEWARRAY> result) {
		final boolean countsNonNegativeBoolean = (Boolean) countsNonNegative.getActualValue();
		result.add(DecisionAlternative_XNEWARRAY.toConcrete(countsNonNegativeBoolean));
	}
	
	protected Outcome decideNewarrayNonconcrete(Primitive countsNonNegative, SortedSet<DecisionAlternative_XNEWARRAY> result) 
	throws DecisionException {
		final boolean shouldRefine;
		final DecisionAlternative_XNEWARRAY OK = DecisionAlternative_XNEWARRAY.toNonconcrete(true);
		final DecisionAlternative_XNEWARRAY WRONG = DecisionAlternative_XNEWARRAY.toNonconcrete(false);

		if (countsNonNegative instanceof Any) {
			//TODO can it really happen? should we throw an exception in the case?
			result.add(WRONG);
			result.add(OK);
			shouldRefine = false;
		} else {
			//TODO what if condition is neither Simplex, nor Any, nor Expression (i.e., FunctionApplication, Widening/NarrowingConversion, PrimitiveSymbolic, Term)?
			try {
				//this implementation saves one sat check in 50% cases
				//(it exploits the fact that if exp is unsat 
				//exp.not() is valid)
				final Expression negative = (Expression) countsNonNegative.not(); 
				if (isSat(negative)) {
					result.add(WRONG);
					final Expression nonNegative = (Expression) countsNonNegative;
					if (isSat(nonNegative)) {
						result.add(OK);
					}
				} else {
					result.add(OK);
				}
				shouldRefine = (result.size() > 1);
			} catch (InvalidTypeException e) {
				//this should never happen as arguments have been checked by the caller
				throw new UnexpectedInternalException(e);
			}
		}
		return Outcome.val(shouldRefine, true);
	}
	
	/**
	 * Decides a store to an array.
	 * 
	 * @param inRange a {@link Primitive} expressing the fact that the access
	 *        index is in the interval 0..array.length. 
	 * @param result a {@link SortedSet}&lt;{@link DecisionAlternative_XASTORE}&gt;, which the method 
	 *            will update by adding to it {@link DecisionAlternative_XASTORE#IN} 
	 *            or {@link DecisionAlternative_XASTORE#OUT} in the case the access may be 
	 *            in range or out of range. Note that the two situations are not
	 *            mutually exclusive.
	 * @return an {@link Outcome}.
	 * @throws InvalidInputException when one of the parameters is incorrect.
	 * @throws DecisionException upon failure.
	 */
	public Outcome decideAstore(Primitive inRange, SortedSet<DecisionAlternative_XASTORE> result)
	throws InvalidInputException, DecisionException {
		if (inRange == null || result == null) {
			throw new InvalidInputException("decideAstore invoked with a null parameter");
		}
		if (inRange.getType() != Type.BOOLEAN) {
			throw new InvalidInputException("inRange type is " + inRange.getType());
		}
		if (inRange instanceof Simplex) {
			decideAstoreConcrete((Simplex) inRange, result);
			return Outcome.val(false, false);
		} else {
			final Outcome o = decideAstoreNonconcrete(inRange, result);
			return o;
		}
	}

	private void decideAstoreConcrete(Simplex inRange, SortedSet<DecisionAlternative_XASTORE> result) {
		final boolean inRangeBoolean = (Boolean) inRange.getActualValue();
		result.add(DecisionAlternative_XASTORE.toConcrete(inRangeBoolean));
	}
	
	protected Outcome decideAstoreNonconcrete(Primitive inRange, SortedSet<DecisionAlternative_XASTORE> result)
	throws DecisionException {
		final boolean shouldRefine;
		final DecisionAlternative_XASTORE IN = DecisionAlternative_XASTORE.toNonconcrete(true);
		final DecisionAlternative_XASTORE OUT = DecisionAlternative_XASTORE.toNonconcrete(false);

		if (inRange instanceof Any) {
			//TODO can it really happen? should we throw an exception in the case?
			result.add(OUT);
			result.add(IN);
			shouldRefine = false;
		} else {
			try {
				//this implementation saves one sat check in 50% cases
				//(it exploits the fact that if exp is unsat 
				//exp.not() is valid)
				final Expression outOfRangeExp = (Expression) inRange.not();
				if (this.isSat(outOfRangeExp)) {
					result.add(OUT);
					final Expression inRangeExp = (Expression) inRange;
					if (this.isSat(inRangeExp)) {
						result.add(IN);
					}
				} else {
					result.add(IN);			
				}
				shouldRefine = (result.size() > 1);
			} catch (InvalidTypeException e) {
				//this should never happen as arguments have been checked by the caller
				throw new UnexpectedInternalException(e);
			}

		}
		return Outcome.val(shouldRefine, true);
	}

	/**
	 * Resolves loading a value to the operand stack, when the value
	 * comes from a local variable or a field.
	 * 
	 * @param state a {@link State}. 
	 * @param valToLoad the {@link Value} returned by the local variable access, 
	 *        that must be loaded on {@code state}'s operand stack. It must not be {@code null}.
	 * @param result a {@link SortedSet}{@code <}{@link DecisionAlternative_XLOAD_GETX}{@code >}, 
	 *        where the method will put all the {@link DecisionAlternative_XLOAD_GETX}s 
	 *        representing all the satisfiable outcomes of the operation.
	 * @return an {@link Outcome}.
	 * @throws InvalidInputException when one of the parameters is incorrect.
	 * @throws DecisionException upon failure.
	 * @throws ClassFileNotFoundException if {@code valToLoad} is a symbolic reference and
	 *         its class name does not correspond to a valid class in the classpath.
	 */
	public Outcome resolveLFLoad(State state, Value valToLoad, SortedSet<DecisionAlternative_XLOAD_GETX> result) 
	throws InvalidInputException, DecisionException, ClassFileNotFoundException {
		if (state == null || valToLoad == null || result == null) {
			throw new InvalidInputException("resolveLFLoad invoked with a null parameter");
		}
		if (Util.isResolved(state, valToLoad)) {
        	result.add(new DecisionAlternative_XLOAD_GETX_Resolved(valToLoad));
        	return Outcome.val(false, false, false);
		} else { 
			return resolveLFLoadUnresolved(state, (ReferenceSymbolic) valToLoad, result);
		}
	}
	
	protected Outcome resolveLFLoadUnresolved(State state, ReferenceSymbolic refToLoad, SortedSet<DecisionAlternative_XLOAD_GETX> result)
	throws DecisionException, ClassFileNotFoundException {
		final boolean partialReferenceResolution = 
			doResolveReference(state, refToLoad, new DecisionAlternativeReferenceFromLocalVariableFactory(), result);
		return Outcome.val(true, partialReferenceResolution, true); //uninitialized symbolic references always require a refinement action
	}
	
	/**
	 * Resolves loading a value to the operand stack, when the value
	 * comes from an array.
	 * 
	 * @param state a {@link State}. 
	 * @param accessExpression an {@link Expression}, the condition under which the 
	 *        array access yields {@code valToLoad} as result. It can be {@code null}, 
	 *        that is equivalent to true but additionally denotes the fact that the 
	 *        array was accessed by a concrete index.
	 * @param valToLoad the {@link Value} returned by the array access 
	 *        when {@code accessExpression} is true,
	 *        or {@code null} to denote an access out of the 
	 *        array bounds.
	 * @param fresh {@code true} iff {@code valToLoad} is fresh, i.e., 
	 *        it had to be assumed by the access and thus it is not yet stored in the 
	 *        {@link Array} object it originates.
	 * @param result a {@link SortedSet}}{@code <}{@link DecisionAlternative_XALOAD}{@code >}, 
	 *        where the method will put all the 
	 *        {@link DecisionAlternative_XALOAD}s representing all the 
	 *        satisfiable outcomes of the operation.
	 * @return an {@link Outcome}.
	 * @throws InvalidInputException when one of the parameters is incorrect.
	 * @throws DecisionException upon failure.
	 * @throws ClassFileNotFoundException if {@code valToLoad} is a symbolic reference and
	 *         its class name does not correspond to a valid class in the classpath.
	 */
	//TODO should be final?
	public Outcome resolveAload(State state, Expression accessExpression, Value valToLoad, boolean fresh, SortedSet<DecisionAlternative_XALOAD> result)
	throws InvalidInputException, DecisionException, ClassFileNotFoundException {
		if (state == null || result == null) {
			throw new InvalidInputException("resolveLFLoad invoked with a null parameter");
		}
		final boolean accessConcrete = (accessExpression == null);
		final boolean accessOutOfBounds = (valToLoad == null);
		final boolean valToLoadResolved = accessOutOfBounds || Util.isResolved(state, valToLoad);
		if (accessConcrete && valToLoadResolved) {
			resolveAloadConcrete(valToLoad, fresh, result);
			return Outcome.val(fresh, false, false); //a fresh value to load always requires a refinement action
		} else if (!accessConcrete && valToLoadResolved) {
			return resolveAloadNonconcrete(accessExpression, valToLoad, fresh, result);
		} else { //!accessOutOfBounds && !Util.isResolved(state, valToLoad)
			return resolveAloadUnresolved(state, accessExpression, (ReferenceSymbolic) valToLoad, fresh, result);
		}
	}
	
	/**
	 * Resolves loading to the operand stack a value from an array, 
	 * in the case the index used for the access is concrete and
	 * the value to load is resolved (i.e., either concrete, or 
	 * a symbolic primitive, or a resolved symbolic reference).
	 * 
	 * @param valToLoad the {@link Value} returned by the array access 
	 *        when {@code accessCondition} is true,
	 *        or {@code null} to denote an access out of the 
	 *        array bounds.
	 * @param fresh {@code true} iff {@code valToLoad} is fresh, i.e., 
	 *        it had to be assumed by the access and thus it is not yet stored in the 
	 *        {@link Array} object it originates.
	 * @param result a {@link SortedSet}}{@code <}{@link DecisionAlternative_XALOAD}{@code >}, 
	 *        where the method will put all the 
	 *        {@link DecisionAlternative_XALOAD}s representing all the 
	 *        satisfiable outcomes of the operation.
	 * @see {@link #resolveAload(State, Expression, Value, boolean, SortedSet)}.
	 */
	private void resolveAloadConcrete(Value valToLoad, boolean fresh, SortedSet<DecisionAlternative_XALOAD> result) {
		final boolean accessOutOfBounds = (valToLoad == null);
		if (accessOutOfBounds) {
			result.add(new DecisionAlternative_XALOAD_Out());
		} else {
			result.add(new DecisionAlternative_XALOAD_Resolved(valToLoad, fresh));
		}
	}

	/**
	 * Resolves loading to the operand stack a value from an array, 
	 * in the case the index used for the access is symbolic and
	 * the value to load is resolved (i.e., either concrete, or 
	 * a symbolic primitive, or a resolved symbolic reference).
	 * 
	 * @param valToLoad the {@link Value} returned by the array access 
	 *        when {@code accessCondition} is true,
	 *        or {@code null} to denote an access out of the 
	 *        array bounds.
	 * @param fresh {@code true} iff {@code valToLoad} is fresh, i.e., 
	 *        it had to be assumed by the access and thus it is not yet stored in the 
	 *        {@link Array} object it originates.
	 * @param result a {@link SortedSet}}{@code <}{@link DecisionAlternative_XALOAD}{@code >}, 
	 *        where the method will put all the 
	 *        {@link DecisionAlternative_XALOAD}s representing all the 
	 *        satisfiable outcomes of the operation.
	 * @see {@link #resolveAload(State, Expression, Value, boolean, SortedSet) resolveAload}.
	 */
	protected Outcome resolveAloadNonconcrete(Expression accessExpression, Value valToLoad, boolean fresh, SortedSet<DecisionAlternative_XALOAD> result)
	throws DecisionException {
		final boolean accessOutOfBounds = (valToLoad == null);
		boolean shouldRefine;
		if (this.isSat(accessExpression)) {
			shouldRefine = fresh; //a fresh value to load always requires a refinement action
			if (accessOutOfBounds) {
				result.add(new DecisionAlternative_XALOAD_Out(accessExpression));
			} else { //Util.isResolved(state, valToLoad))
				result.add(new DecisionAlternative_XALOAD_Resolved(accessExpression, valToLoad, fresh));
			}
		} else {
			shouldRefine = false;
			//nothing to add to result
		}

		return Outcome.val(shouldRefine, false, true);
	}

	protected Outcome resolveAloadUnresolved(State state, Expression accessCondition, ReferenceSymbolic refToLoad, boolean fresh, SortedSet<DecisionAlternative_XALOAD> result)
	throws DecisionException, ClassFileNotFoundException {
		final boolean accessConcrete = (accessCondition == null);
		final boolean shouldRefine;
		final boolean noReferenceExpansion;
		if (accessConcrete || this.isSat(accessCondition)) {
			shouldRefine = true; //unresolved symbolic references always require a refinement action
			noReferenceExpansion =
				doResolveReference(state, refToLoad, new DecisionAlternativeReferenceFromArrayFactory(accessCondition), result);
		} else {
			shouldRefine = false;
			noReferenceExpansion = true;
			//accessCondition is unsatisfiable: nothing to add to result
		}
		return Outcome.val(shouldRefine, noReferenceExpansion, true);
	}
	
	/**
	 * Completes the set operation of an {@link Array} by constraining the affected entries
	 * and removing the unsatisfiable ones.
	 * 
	 * @param entries an {@link Iterator}{@code <}{@link AccessOutcomeIn}{@code >}. The method
	 *        will determine the items affected by the set operation, constrain them, and 
	 *        delete all the entries which after constraining become unsatisfiable.
	 * @param index a {@link Primitive}, the position in the {@link Array} which is set.
	 * @throws InvalidInputException when one of the parameters is incorrect.
	 * @throws DecisionException upon failure.
	 */
	public void completeArraySet(Iterator<Array.AccessOutcomeIn> entries, Primitive index) 
	throws InvalidInputException, DecisionException {
		if (entries == null || index == null) {
			throw new InvalidInputException("completeArraySet invoked with a null parameter");
		}
		if (index.getType() != Type.INT) {
			throw new InvalidInputException("index of array access has type " + index.getType());
		}
		try {
			while (entries.hasNext()) {
				final Array.AccessOutcomeIn e = entries.next();
				final Primitive indexInRange = e.inRange(index);
				final boolean entryAffected = this.isSat((Expression) indexInRange);

				//if the entry is affected, it is constrained and possibly removed
				if (entryAffected) {
					e.constrainExpression(index); //TODO possibly move this back to Array?
					final Expression rangeConstrained = e.getAccessCondition();
					if (this.isSat(rangeConstrained)) {
						//do nothing
					} else {
						entries.remove();
					}
				}

			}
		} catch (InvalidOperandException | InvalidTypeException exc) {
			//this should never happen after argument check
			throw new UnexpectedInternalException(exc);
		}
		//TODO coalesce entries that have same value (after investigating the impact on guided execution)
	}
	
	/**
	 * Resolution of unresolved references.
	 * 
	 * @param <D> The class for the decision alternatives.
	 * @param <DA> The class for the decision alternative for 
	 *            resolutions by aliasing. Must be a subclass of {@code <D>}.
	 * @param <DE> The class for the decision alternative for
	 *            resolutions by expansion. Must be a subclass of {@code <D>}.
	 * @param <DN> The class for the decision alternative for
	 *            resolutions by null. Must be a subclass of {@code <D>}.
	 * @param state a {@link State}.
	 * @param refToResolve the {@link ReferenceSymbolic} to resolve.
	 * @param factory A Concrete Factory for decision alternatives.
	 * @param result a {@link SortedSet}{@code <D>}, which the method 
	 *            will update by adding to it all the decision alternatives 
	 *            representing all the valid expansions of {@code notInitializedRef}.
	 * @return {@code true} iff the resolution of the reference is 
	 *         partial (see {@link Outcome#noReferenceExpansion()}).
	 * @throws DecisionException
	 * @throws ClassFileNotFoundException 
	 */
	protected <D, DA extends D, DE extends D, DN extends D> 
	boolean doResolveReference(State state, ReferenceSymbolic refToResolve, 
	DecisionAlternativeReferenceFactory<DA, DE, DN> factory, SortedSet<D> result) 
	throws DecisionException, ClassFileNotFoundException {
		//gets the statically compatible possible aliases 
		//and expansions of refToResolve
		final TreeMap<Long, Objekt> possibleAliases = getPossibleAliases(state, refToResolve);
		final Set<String> possibleExpansions = getPossibleExpansions(state, refToResolve);
		if (possibleAliases == null || possibleExpansions == null) {
			throw new UnexpectedInternalException("Symbolic reference " + refToResolve + 
					" (" + refToResolve.getOrigin() + ") has a bad type.");
		}
		
		int branchNumber = 1;
		//filters static aliases based on their satisfiability
		for (Map.Entry<Long, Objekt> ae : possibleAliases.entrySet()) {
			final long i = ae.getKey();
			final Objekt o = ae.getValue();
			if (isSatAliases(refToResolve, i, o)) {
				final DA a = factory.createAlternativeRefAliases(refToResolve, i, o.getOrigin(), branchNumber);
				result.add(a);
			}
			++branchNumber;
		}
		
		//same for static expansions
		boolean partialReferenceResolution = true;
		for (String className : possibleExpansions) {
			if (isSatInitialized(className) && isSatExpands(refToResolve, className)) {
				final DE e = factory.createAlternativeRefExpands(refToResolve, className, branchNumber);
				result.add(e);
				partialReferenceResolution = false;
			}
			++branchNumber;
		}
		
		//same for null
		if (isSatNull(refToResolve)) {
			final DN n = factory.createAlternativeRefNull(refToResolve, branchNumber);
			result.add(n);
			//no need to increment branchNumber
		}
		
		//is there a partial reference resolution?
		return partialReferenceResolution;
	}
	
	/**
	 * Returns all the heap objects in a state that may be possible
	 * aliases of a given {@link ReferenceSymbolic}.
	 *
	 * @param state a {@link State}.
	 * @param ref a {@link ReferenceSymbolic} to be resolved.
	 * @return a {@link TreeMap}{@code <}{@link Long}{@code, }{@link Objekt}{@code >}, 
	 *         representing the subview of {@code state}'s heap that contains
	 *         all the objects that are compatible, in their type and epoch, with {@code ref}.
	 *         If {@code ref} does not denote a reference or array type, the method 
	 *         returns {@code null}.
	 */
	private TreeMap<Long, Objekt> getPossibleAliases(State state, ReferenceSymbolic ref) {
		//checks preconditions
		final String type = ref.getStaticType();
		if (!Type.isReference(type) && !Type.isArray(type)) {
			return null;
		}
		
		final TreeMap<Long, Objekt> retVal = new TreeMap<>();

    	//TODO extract this code and share with State.getObjectInitial
		//scans the path condition for compatible objects
		final ClassHierarchy classHierarchy = state.getClassHierarchy();
		final Iterable<Clause> pathCondition = state.getPathCondition();
		for (Clause c : pathCondition) {
			if (c instanceof ClauseAssumeExpands) {
				//gets the object and its position in the heap
				final ClauseAssumeExpands cExp = (ClauseAssumeExpands) c;
				final Long i = cExp.getHeapPosition();
				final Objekt o = cExp.getObjekt();

				//if it is time and epoch compatible, adds the object
				//to the result
				if (isTypeAndEpochCompatible(o, ref, classHierarchy)) {
					retVal.put(i, o);
				}
			}
		}
		return retVal;
	}
	
	/**
	 * Checks whether an {@link Objekt} can be used 
	 * to resolve of a symbolic reference.
	 * 
	 * @param o an {@link Objekt}.
	 * @param ref a {@link ReferenceSymbolic}.
	 * @param classHierarchy a {@link ClassHierarchy}.
	 * @return {@code true} iff {@code ref} can be resolved by {@code o}. 
	 *         More precisely, returns {@code true} iff the creation epoch of 
	 *         {@code o} comes before that of {@code ref}, and {@code o}'s type 
	 *         is a subtype of the static type of {@code ref}.
	 */
	private static boolean isTypeAndEpochCompatible(Objekt o, ReferenceSymbolic ref, ClassHierarchy classHierarchy) {
		final String type = ref.getStaticType();
		final String className = Type.className(type);
		return (o.isSymbolic() && classHierarchy.isSubclass(o.getType(), className));
	}

	/**
	 * Returns all the heap objects in a state that may be possible
	 * aliases of a given {@link ReferenceSymbolic}.
	 *
	 * @param state a {@link State}.
	 * @param ref a {@link ReferenceSymbolic} to be resolved.
	 * @return a {@link Set}{@code <}{@link String}{@code >}, listing
	 *         all the classes that are compatible, in their type and epoch of 
	 *         initialization, with {@code ref}.
	 *         If {@code ref} does not denote a reference or array type, the method 
	 *         returns {@code null}.
	 * @throws ClassFileNotFoundException if {@code ref}'s class name does not
	 *         denote a valid class in the classpath.
	 */
	private Set<String> getPossibleExpansions(State state, ReferenceSymbolic ref) 
	throws ClassFileNotFoundException {
		final String type = ref.getStaticType();
		if (!Type.isReference(type) && !Type.isArray(type)) {
			return null;
		}
		
		final Set<String> retVal;
		if (Type.isArray(type)) {
			//the (really trivial) array case:
			//array classes are final, concrete, and can
			//always be assumed to be initialized, so
			//this is the only expansion possible
			retVal = new HashSet<>();
			retVal.add(type);
		} else {
			final String className = Type.getReferenceClassName(type);
			retVal = state.getClassHierarchy().getAllConcreteSubclasses(className);
		}
		
		return retVal;
	}
}