package jbse.algo;

import jbse.common.exc.UnexpectedInternalException;
import jbse.dec.exc.DecisionException;
import jbse.mem.State;
import jbse.mem.exc.ContradictionException;
import jbse.tree.DecisionAlternative_XLOAD_GETX;
import jbse.tree.DecisionAlternative_XLOAD_GETX_RefAliases;
import jbse.tree.DecisionAlternative_XLOAD_GETX_RefNull;
import jbse.tree.DecisionAlternative_XLOAD_GETX_RefExpands;
import jbse.tree.DecisionAlternative_XLOAD_GETX_Resolved;
import jbse.tree.VisitorDecisionAlternative_XLOAD_GETX;
import jbse.val.exc.InvalidTypeException;

/**
 * Strategy for refining a state for the *load* and get* bytecodes 
 * ([a/d/f/i/l]load[_0/1/2/3], get[field/static]). Reimplements 
 * {@link StrategyRefine#refine} to redispatch towards 
 * abstract methods specializing refinement on the possible 
 * {@link DecisionAlternative_XLOAD_GETX}s. This class exists
 * only to untangle a bit its only subclass.
 * 
 * @author Pietro Braione
 *
 */
abstract class StrategyRefine_XLOAD_GETX implements StrategyRefine<DecisionAlternative_XLOAD_GETX> {
	abstract public void refineRefExpands(State s, DecisionAlternative_XLOAD_GETX_RefExpands drc)
	throws DecisionException, ContradictionException, InvalidTypeException;

	abstract public void refineRefAliases(State s, DecisionAlternative_XLOAD_GETX_RefAliases dro) 
	throws DecisionException, ContradictionException;

	abstract public void refineRefNull(State s, DecisionAlternative_XLOAD_GETX_RefNull drn)
	throws DecisionException, ContradictionException;

	abstract public void refineResolved(State s, DecisionAlternative_XLOAD_GETX_Resolved drr)
	throws DecisionException;

	@Override
	public final void refine(final State s, DecisionAlternative_XLOAD_GETX r)
	throws DecisionException, ContradictionException, InvalidTypeException {
		//a visitor redispatching to the methods which specialize this.refine
		final VisitorDecisionAlternative_XLOAD_GETX visitorRefine = 
		new VisitorDecisionAlternative_XLOAD_GETX() {
			@Override
			public void visitDecisionAlternative_XLOAD_GETX_RefExpands(DecisionAlternative_XLOAD_GETX_RefExpands drc) 
			throws DecisionException, ContradictionException, InvalidTypeException {
				StrategyRefine_XLOAD_GETX.this.refineRefExpands(s, drc);
			}

			@Override
			public void visitDecisionAlternative_XLOAD_GETX_RefAliases(DecisionAlternative_XLOAD_GETX_RefAliases dro) 
			throws DecisionException, ContradictionException {
				StrategyRefine_XLOAD_GETX.this.refineRefAliases(s, dro);
			}

			@Override
			public void visitDecisionAlternative_XLOAD_GETX_RefNull(DecisionAlternative_XLOAD_GETX_RefNull drn) 
			throws DecisionException, ContradictionException {
				StrategyRefine_XLOAD_GETX.this.refineRefNull(s, drn);
			}

			@Override
			public void visitDecisionAlternative_XLOAD_GETX_Resolved(DecisionAlternative_XLOAD_GETX_Resolved drr)
			throws DecisionException {
				StrategyRefine_XLOAD_GETX.this.refineResolved(s, drr);
			}
		};

		//redispatches and manages exceptions
		try {
			r.accept(visitorRefine);
		} catch (DecisionException | ContradictionException | 
				InvalidTypeException | RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new UnexpectedInternalException(e);
		}
	}
}
