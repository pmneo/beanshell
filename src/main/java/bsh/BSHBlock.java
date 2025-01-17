/*****************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one                *
 * or more contributor license agreements.  See the NOTICE file              *
 * distributed with this work for additional information                     *
 * regarding copyright ownership.  The ASF licenses this file                *
 * to you under the Apache License, Version 2.0 (the                         *
 * "License"); you may not use this file except in compliance                *
 * with the License.  You may obtain a copy of the License at                *
 *                                                                           *
 *     http://www.apache.org/licenses/LICENSE-2.0                            *
 *                                                                           *
 * Unless required by applicable law or agreed to in writing,                *
 * software distributed under the License is distributed on an               *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY                    *
 * KIND, either express or implied.  See the License for the                 *
 * specific language governing permissions and limitations                   *
 * under the License.                                                        *
 *                                                                           *
 *                                                                           *
 * This file is part of the BeanShell Java Scripting distribution.           *
 * Documentation and updates may be found at http://www.beanshell.org/       *
 * Patrick Niemeyer (pat@pat.net)                                            *
 * Author of Learning Java, O'Reilly & Associates                            *
 *                                                                           *
 *****************************************************************************/



package bsh;

import java.util.ArrayList;
import java.util.List;

class BSHBlock extends SimpleNode
{
    public boolean isSynchronized = false;
    public boolean isStatic = false;
    private boolean hasClassDeclaration = false, isFirst = true;

    BSHBlock(int id) { super(id); }

    public Object eval( CallStack callstack, Interpreter interpreter)
        throws EvalError
    {
        return eval( callstack, interpreter, false );
    }

    /**
        @param overrideNamespace if set to true the block will be executed
        in the current namespace (not a subordinate one).
        <p>
        If true *no* new BlockNamespace will be swapped onto the stack and
        the eval will happen in the current
        top namespace.  This is used by BshMethod, TryStatement, etc.
        which must intialize the block first and also for those that perform
        multiple passes in the same block.
    */
    public Object eval(
        CallStack callstack, Interpreter interpreter,
        boolean overrideNamespace )
        throws EvalError
    {
        interpreter.checkInterrupted( this, callstack );

        Object syncValue = null;
        if ( isSynchronized )
        {
            // First node is the expression on which to sync
            Node exp = jjtGetChild(0);
            syncValue = exp.eval(callstack, interpreter);
        }

        interpreter.checkInterrupted( this, callstack );

        Object ret;
        if ( isSynchronized ) // Do the actual synchronization
            synchronized( syncValue )
            {
                ret = evalBlock(
                    callstack, interpreter, overrideNamespace, null/*filter*/);
            }
        else
                ret = evalBlock(
                    callstack, interpreter, overrideNamespace, null/*filter*/);

        interpreter.checkInterrupted( this, callstack );

        return ret;
    }

    Object evalBlock(
        CallStack callstack, Interpreter interpreter,
        boolean overrideNamespace, NodeFilter nodeFilter )
        throws EvalError
    {
        Object ret = Primitive.VOID;
        NameSpace enclosingNameSpace = null;
        if ( !overrideNamespace ) {
            NameSpace nsTop = callstack.top();

            NameSpace blockNs = nsTop.getBlockNs();
            if( blockNs == null ) {
                nsTop.setBlockNs( blockNs = new BlockNameSpace( nsTop ) );
            }

            enclosingNameSpace = callstack.swap( blockNs );
        }

        int startChild = isSynchronized ? 1 : 0;
        int numChildren = jjtGetNumChildren();

        try {
            /*
                Evaluate block in two passes:
                First do class declarations then do everything else.
            */
            if (isFirst || hasClassDeclaration)
            for(int i=startChild; i<numChildren; i++)
            {
                Node node = jjtGetChild(i);

                if ( nodeFilter != null && !nodeFilter.isVisible( node ) )
                    continue;

                if ( node instanceof BSHClassDeclaration ) {
                    hasClassDeclaration = true;
                    node.eval( callstack, interpreter );
                }

                interpreter.checkInterrupted( this, callstack );
            }

            List<Node> enumBlocks = null;

            for(int i=startChild; i<numChildren; i++)
            {
                Node node = jjtGetChild(i);

                if ( node instanceof BSHClassDeclaration )
                    continue;

                // filter nodes
                if ( nodeFilter != null && !nodeFilter.isVisible( node ) )
                    continue;

                // enum blocks need to override enum class members
                // let the class finish initializing first
                if (node instanceof BSHEnumConstant) {
                    if( enumBlocks == null ) {
                        enumBlocks = new ArrayList<>();
                    }
                    enumBlocks.add(node);
                    continue;
                }

                ret = node.eval( callstack, interpreter );

                interpreter.checkInterrupted( this, callstack );

                // statement or embedded block evaluated a return statement
                if ( ret instanceof ReturnControl )
                    break;
            }
            if( enumBlocks != null ) {
                while (!enumBlocks.isEmpty() ) {
                    enumBlocks.remove(0).eval( callstack, interpreter );
                }
            }
        } finally {
            // make sure we put the namespace back when we leave.
            // clear cached block name space, store as empty
            if ( !overrideNamespace ) {
                callstack.top().clear();
                callstack.swap( enclosingNameSpace );
            }
        }
        isFirst = false;

        interpreter.checkInterrupted( this, callstack );

        return ret;
    }

    public interface NodeFilter {
        boolean isVisible( Node node );
    }

}
