package edu.fdu.se.astdiff.miningactions.statement;

import java.util.ArrayList;
import java.util.List;

import com.github.gumtreediff.actions.model.Action;

import com.github.gumtreediff.tree.Tree;
import com.github.javaparser.Range;
import edu.fdu.se.astdiff.miningactions.bean.ChangePacket;
import edu.fdu.se.astdiff.miningactions.bean.MiningActionData;
import edu.fdu.se.astdiff.miningactions.util.AstRelations;
import edu.fdu.se.astdiff.miningactions.util.DefaultDownUpTraversal;
import edu.fdu.se.astdiff.miningactions.util.DefaultUpDownTraversal;
import edu.fdu.se.astdiff.miningoperationbean.ClusteredActionBean;
import edu.fdu.se.astdiff.miningoperationbean.OperationTypeConstants;
import edu.fdu.se.astdiff.miningoperationbean.statementplus.ExpressionChangeEntity;
import edu.fdu.se.astdiff.miningoperationbean.statementplus.VariableChangeEntity;

public class MatchVariableDeclarationExpression {

	public static void matchVariableDeclaration(MiningActionData fp, Action a) {
		ChangePacket changePacket = new ChangePacket();
		List<Action> subActions = new ArrayList<>();
		changePacket.setOperationType(OperationTypeConstants.getEditTypeIntCode(a));
		changePacket.setOperationEntity(OperationTypeConstants.ENTITY_STATEMENT_TYPE_I);
		DefaultUpDownTraversal.traverseTypeIStatements(a,subActions,changePacket);
		fp.setActionTraversedMap(subActions);
		Range range = AstRelations.getRangeOfAstNode(a);
		ClusteredActionBean mBean = new ClusteredActionBean(ClusteredActionBean.TRAVERSE_UP_DOWN,a,subActions,changePacket,range);
		VariableChangeEntity code = new VariableChangeEntity(mBean);
		fp.addOneChangeEntity(code);

	}

	public static void matchVariableDeclarationNewEntity(MiningActionData fp, Action a, Tree fafather) {
		ChangePacket changePacket = new ChangePacket();
		List<Action> sameEdits = new ArrayList<>();
		changePacket.setOperationEntity(OperationTypeConstants.ENTITY_STATEMENT_TYPE_I);
		DefaultDownUpTraversal.traverseFatherNodeGetSameNodeActions(fafather,sameEdits,changePacket);
		fp.setActionTraversedMap(sameEdits);
		Range range = AstRelations.getRangeOfAstNode(a);
		ClusteredActionBean mBean = new ClusteredActionBean(ClusteredActionBean.TRAVERSE_DOWN_UP,a,sameEdits,changePacket,range,fafather);
		ExpressionChangeEntity code = new ExpressionChangeEntity(mBean);
		fp.addOneChangeEntity(code);
	}
}