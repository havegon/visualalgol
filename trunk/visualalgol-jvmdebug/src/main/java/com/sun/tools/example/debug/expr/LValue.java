/*
 * @(#)LValue.java	1.29 05/11/17
 *
 * Copyright 2006 Sun Microsystems, Inc. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
/*
 * Copyright (c) 1997-1999 by Sun Microsystems, Inc. All Rights Reserved.
 * 
 * Sun grants you ("Licensee") a non-exclusive, royalty free, license to use,
 * modify and redistribute this software in source and binary code form,
 * provided that i) this copyright notice and license appear on all copies of
 * the software; and ii) Licensee does not utilize the software in a manner
 * which is disparaging to Sun.
 * 
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING ANY
 * IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR
 * NON-INFRINGEMENT, ARE HEREBY EXCLUDED. SUN AND ITS LICENSORS SHALL NOT BE
 * LIABLE FOR ANY DAMAGES SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING
 * OR DISTRIBUTING THE SOFTWARE OR ITS DERIVATIVES. IN NO EVENT WILL SUN OR ITS
 * LICENSORS BE LIABLE FOR ANY LOST REVENUE, PROFIT OR DATA, OR FOR DIRECT,
 * INDIRECT, SPECIAL, CONSEQUENTIAL, INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER
 * CAUSED AND REGARDLESS OF THE THEORY OF LIABILITY, ARISING OUT OF THE USE OF
 * OR INABILITY TO USE SOFTWARE, EVEN IF SUN HAS BEEN ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGES.
 * 
 * This software is not designed or intended for use in on-line control of
 * aircraft, air traffic, aircraft navigation or aircraft communications; or in
 * the design, construction, operation or maintenance of any nuclear
 * facility. Licensee represents and warrants that it will not use or
 * redistribute the Software for such purposes.
 */

package com.sun.tools.example.debug.expr;

import com.sun.jdi.*;
import java.util.*;

abstract class LValue {

    // The JDI Value object for this LValue.  Once we have this Value,
    // we have to remember it since after we return the LValue object
    // to the ExpressionParser, it might decide that it needs 
    // the 'toString' value for the LValue in which case it will
    // call getMassagedValue to get this toString value.  At that
    // point, we don't want to call JDI a 2nd time to get the Value
    // for the LValue.  This is especially wrong when the LValue
    // represents a member function.  We would end up calling it
    // a 2nd time.
    //
    // Unfortunately, there are several levels of calls to
    // get/set values in this file.  To minimize confusion,
    // jdiValue is set/tested at the lowest level - right
    // next to the actual calls to JDI methods to get/set the
    // value in the debuggee.  
    protected Value jdiValue;

    abstract Value getValue() throws InvocationException, 
                                     IncompatibleThreadStateException,
                                     InvalidTypeException,
                                     ClassNotLoadedException,
                                     ParseException;

    abstract void setValue0(Value value) 
                   throws ParseException, InvalidTypeException, 
                          ClassNotLoadedException;

    abstract void invokeWith(List arguments) throws ParseException;

    void setValue(Value value) throws ParseException {
        try {
            setValue0(value);
        } catch (InvalidTypeException exc) {
            throw new ParseException(
                "Attempt to set value of incorrect type" +
                exc);
        } catch (ClassNotLoadedException exc) {
            throw new ParseException(
                "Attempt to set value before " + exc.className() + " was loaded" +
                exc);
        }
    }        

    void setValue(LValue lval) throws ParseException {
        setValue(lval.interiorGetValue());
    }

    LValue memberLValue(ExpressionParser.GetFrame frameGetter, 
                        String fieldName) throws ParseException {
        try {
            return memberLValue(fieldName, frameGetter.get().thread());
        } catch (IncompatibleThreadStateException exc) {
            throw new ParseException("Thread not suspended");
        }
    }

    LValue memberLValue(String fieldName, ThreadReference thread) throws ParseException {
        
        Value val = interiorGetValue();
        if ((val instanceof ArrayReference) &&
            "length".equals(fieldName)){
            return new LValueArrayLength((ArrayReference)val);
        }
        return new LValueInstanceMember(val, fieldName, thread);
    }

    // Return the Value for this LValue that would be used to concatenate
    // to a String.  IE, if it is an Object, call toString in the debuggee.
    Value getMassagedValue(ExpressionParser.GetFrame frameGetter) throws ParseException {
        Value vv = interiorGetValue();

        // If vv is an ObjectReference, then we have to
        // do the implicit call to toString().
        if (vv instanceof ObjectReference &&
            !(vv instanceof StringReference) && 
            !(vv instanceof ArrayReference)) {
            StackFrame frame;
            try {
                frame = frameGetter.get();
            } catch (IncompatibleThreadStateException exc) {
                throw new ParseException("Thread not suspended");
            }

            ThreadReference thread = frame.thread();
            LValue toStringMember = memberLValue("toString", thread);
            toStringMember.invokeWith(new ArrayList());
            return toStringMember.interiorGetValue();
        }
        return vv;
    }

    Value interiorGetValue() throws ParseException {
        Value value;
        try {
            value = getValue();
        } catch (InvocationException e) {
            throw new ParseException("Unable to complete expression. Exception " +
                                     e.exception() + " thrown");
        } catch (IncompatibleThreadStateException itse) {
            throw new ParseException("Unable to complete expression. Thread " +
                                     "not suspended for method invoke");
        } catch (InvalidTypeException ite) {
            throw new ParseException("Unable to complete expression. Method " +
                                     "argument type mismatch");
        } catch (ClassNotLoadedException tnle) {
            throw new ParseException("Unable to complete expression. Method " +
                                     "argument type " + tnle.className() + 
                                     " not yet loaded");
        }
        return value;
    }

    LValue arrayElementLValue(LValue lval) throws ParseException {
        Value indexValue = lval.interiorGetValue();
        int index;
        if ( (indexValue instanceof IntegerValue) ||
             (indexValue instanceof ShortValue) ||
             (indexValue instanceof ByteValue) ||
             (indexValue instanceof CharValue) ) {
            index = ((PrimitiveValue)indexValue).intValue();
        } else {
            throw new ParseException("Array index must be a integer type");
        }
        return new LValueArrayElement(interiorGetValue(), index);
    }

    public String toString() {
        try {
            return interiorGetValue().toString();
        } catch (ParseException e) {
            return "<Parse Exception>";
        }
    }

    static final int STATIC = 0;
    static final int INSTANCE = 1;

    static Field fieldByName(ReferenceType refType, String name, int kind) {
        /*
         * TO DO: Note that this currently fails to find superclass
         * or implemented interface fields. This is due to a temporary
         * limititation of RefType.fieldByName. Once that method is 
         * fixed, superclass fields will be found.
         */
        Field field = refType.fieldByName(name);
        if (field != null) {
            boolean isStatic = field.isStatic();
            if (((kind == STATIC) && !isStatic) || 
                ((kind == INSTANCE) && isStatic)) {
                field = null;
            }
        }
/***
        System.err.println("fieldByName: " + refType.name() + " " +
                                             name + " " +
                                             kind + " " + 
                                             (field != null));
***/
        return field;
    }

    static List methodsByName(ReferenceType refType, String name, int kind) {
        List list = refType.methodsByName(name);
        Iterator iter = list.iterator();
        while (iter.hasNext()) {
            Method method = (Method)iter.next();
            boolean isStatic = method.isStatic();
            if (((kind == STATIC) && !isStatic) || 
                ((kind == INSTANCE) && isStatic)) {
                iter.remove();
            }
        }
        return list;
    }

    static List primitiveTypeNames = new ArrayList();
    static {
        primitiveTypeNames.add("boolean");
        primitiveTypeNames.add("byte");
        primitiveTypeNames.add("char");
        primitiveTypeNames.add("short");
        primitiveTypeNames.add("int");
        primitiveTypeNames.add("long");
        primitiveTypeNames.add("float");
        primitiveTypeNames.add("double");
    }


    static final int SAME = 0;
    static final int ASSIGNABLE = 1;
    static final int DIFFERENT = 2;
    /*
     * Return SAME, DIFFERENT or ASSIGNABLE.
     * SAME means each arg type is the same as type of the corr. arg.
     * ASSIGNABLE means that not all the pairs are the same, but
     * for those that aren't, at least the argType is assignable
     * from the type of the argument value.
     * DIFFERENT means that in at least one pair, the
     * argType is not assignable from the type of the argument value.
     * IE, one is an Apple and the other is an Orange.
     */
    static int argumentsMatch(List argTypes, List arguments) {
        if (argTypes.size() != arguments.size()) {
            return DIFFERENT;
        }

        Iterator typeIter = argTypes.iterator();
        Iterator valIter = arguments.iterator();
        int result = SAME;

        // If any pair aren't the same, change the
        // result to ASSIGNABLE.  If any pair aren't
        // assignable, return DIFFERENT
        while (typeIter.hasNext()) {
            Type argType = (Type)typeIter.next();
            Value value = (Value)valIter.next();
            if (value == null) {
                // Null values can be passed to any non-primitive argument
                if (primitiveTypeNames.contains(argType.name())) {
                    return DIFFERENT;
                }
                // Else, we will assume that a null value
                // exactly matches an object type.
            }
            if (!value.type().equals(argType)) {
                if (isAssignableTo(value.type(), argType)) {
                    result = ASSIGNABLE;
                } else {
                    return DIFFERENT;
                }
            }
        }
        return result;
    }


    // These is...AssignableTo methods are based on similar code in the JDI
    // implementations of ClassType, ArrayType, and InterfaceType

    static boolean isComponentAssignable(Type fromType, Type toType) {
        if (fromType instanceof PrimitiveType) {
            // Assignment of primitive arrays requires identical
            // component types.
            return fromType.equals(toType);
        }
        if (toType instanceof PrimitiveType) {
            return false;
        }
        // Assignment of object arrays requires availability
        // of widening conversion of component types
        return isAssignableTo(fromType, toType);
    }

    static boolean isArrayAssignableTo(ArrayType fromType, Type toType) {
        if (toType instanceof ArrayType) {
            try {
                Type toComponentType = ((ArrayType)toType).componentType();
                return isComponentAssignable(fromType.componentType(), toComponentType);
            } catch (ClassNotLoadedException e) {
                // One or both component types has not yet been 
                // loaded => can't assign
                return false;
            }
        }
        if (toType instanceof InterfaceType) {
            // Only valid InterfaceType assignee is Cloneable
            return toType.name().equals("java.lang.Cloneable");
        } 
        // Only valid ClassType assignee is Object
        return toType.name().equals("java.lang.Object");
    }

    static boolean isAssignableTo(Type fromType, Type toType) {
        if (fromType.equals(toType)) {
            return true;
        }

        // If one is boolean, so must be the other.
        if (fromType instanceof BooleanType) {
            if (toType instanceof BooleanType) {
                return true;
            }
            return false;
        }
        if (toType instanceof BooleanType) {
            return false;
        }

        // Other primitive types are intermixable only with each other.
        if (fromType instanceof PrimitiveType) {
            if (toType instanceof PrimitiveType) {
                return true;
            }
            return false;
        } 
        if (toType instanceof PrimitiveType) {
            return false;
        }

        // neither one is primitive.
        if (fromType instanceof ArrayType) {
            return isArrayAssignableTo((ArrayType)fromType, toType);
        }
        List interfaces;
        if (fromType instanceof ClassType) {
            ClassType superclazz = ((ClassType)fromType).superclass();
            if ((superclazz != null) && isAssignableTo(superclazz, toType)) {
                return true;
            }
            interfaces = ((ClassType)fromType).interfaces();
        } else {
            // fromType must be an InterfaceType
            interfaces = ((InterfaceType)fromType).superinterfaces();
        }
        Iterator iter = interfaces.iterator();
        while (iter.hasNext()) {
            InterfaceType interfaze = (InterfaceType)iter.next();
            if (isAssignableTo(interfaze, toType)) {
                return true;
            }
        }
        return false;
    }

    static Method resolveOverload(List overloads, List arguments) 
                                       throws ParseException {

        // If there is only one method to call, we'll just choose
        // that without looking at the args.  If they aren't right
        // the invoke will return a better error message than we
        // could generate here.
        if (overloads.size() == 1) {
            return (Method)overloads.get(0);
        }

        // Resolving overloads is beyond the scope of this exercise.
        // So, we will look for a method that matches exactly the
        // types of the arguments.  If we can't find one, then
        // if there is exactly one method whose param types are assignable
        // from the arg types, we will use that.  Otherwise,
        // it is an error.  We won't guess which of multiple possible
        // methods to call. And, since casts aren't implemented,
        // the user can't use them to pick a particular overload to call.
        // IE, the user is out of luck in this case.
        Iterator iter = overloads.iterator();
        Method retVal = null;
        int assignableCount = 0;
        while (iter.hasNext()) {
            Method mm = (Method)iter.next();
            List argTypes;
            try {
                argTypes = mm.argumentTypes();
            } catch (ClassNotLoadedException ee) {
                // This probably won't happen for the
                // method that we are really supposed to
                // call.
                continue;
            }
            int compare = argumentsMatch(argTypes, arguments);
            if (compare == SAME) {
                return mm;
            }
            if (compare == DIFFERENT) {
                continue;
            }
            // Else, it is assignable.  Remember it.
            retVal = mm;
            assignableCount++;
        }

        // At this point, we didn't find an exact match,
        // but we found one for which the args are assignable.
        // 
        if (retVal != null) {
            if (assignableCount == 1) {
                return retVal;
            }
            throw new ParseException("Arguments match multiple methods");
        }
        throw new ParseException("Arguments match no method");
    }

    private static class LValueLocal extends LValue {
        final StackFrame frame;
        final LocalVariable var;

        LValueLocal(StackFrame frame, LocalVariable var) {
            this.frame = frame;
            this.var = var;
        }
        
        Value getValue() {
            if (jdiValue == null) {
                jdiValue = frame.getValue(var);
            }
            return jdiValue;
        }

        void setValue0(Value val) throws InvalidTypeException, 
                                         ClassNotLoadedException {
            frame.setValue(var, val);
            jdiValue = val;
        }

        void invokeWith(List arguments) throws ParseException {
            throw new ParseException(var.name() + " is not a method");
        }
    }

    private static class LValueInstanceMember extends LValue {
        final ObjectReference obj;
        final ThreadReference thread;
        final Field matchingField;
        final List overloads;
        Method matchingMethod = null;
        List methodArguments = null;

        LValueInstanceMember(Value value, 
                            String memberName, 
                            ThreadReference thread) throws ParseException {
            if (!(value instanceof ObjectReference)) {
                throw new ParseException(
                       "Cannot access field of primitive type: " + value);
            }
            this.obj = (ObjectReference)value;
            this.thread = thread;
            ReferenceType refType = obj.referenceType();
            /*
             * Can't tell yet whether this LValue will be accessed as a
             * field or method, so we keep track of all the possibilities
             */
            matchingField = LValue.fieldByName(refType, memberName, 
                                               LValue.INSTANCE);
            overloads = LValue.methodsByName(refType, memberName,
                                              LValue.INSTANCE);
            if ((matchingField == null) && overloads.size() == 0) {
                throw new ParseException("No instance field or method with the name "
                               + memberName + " in " + refType.name());
            }
        }
        
        Value getValue() throws InvocationException, InvalidTypeException,
                                ClassNotLoadedException, IncompatibleThreadStateException,
                                ParseException {
            if (jdiValue != null) {
                return jdiValue;
            }
            if (matchingMethod == null) {
                if (matchingField == null) {
                    throw new ParseException("No such field in " + obj.referenceType().name());
                }
                return jdiValue = obj.getValue(matchingField);
            } else {
                return jdiValue = obj.invokeMethod(thread, matchingMethod, methodArguments, 0);
            }
        }

        void setValue0(Value val) throws ParseException, 
                                         InvalidTypeException,
                                        ClassNotLoadedException {
            if (matchingMethod != null) {
                throw new ParseException("Cannot assign to a method invocation");
            }
            obj.setValue(matchingField, val);
            jdiValue = val;
        }

        void invokeWith(List arguments) throws ParseException {
            if (matchingMethod != null) {
                throw new ParseException("Invalid consecutive invocations");
            }
            methodArguments = arguments;
            matchingMethod = LValue.resolveOverload(overloads, arguments);
        }
    }

    private static class LValueStaticMember extends LValue {
        final ReferenceType refType;
        final ThreadReference thread;
        final Field matchingField;
        final List overloads;
        Method matchingMethod = null;
        List methodArguments = null;

        LValueStaticMember(ReferenceType refType, 
                          String memberName,
                          ThreadReference thread) throws ParseException {
            this.refType = refType;
            this.thread = thread;
            /*
             * Can't tell yet whether this LValue will be accessed as a
             * field or method, so we keep track of all the possibilities
             */
            matchingField = LValue.fieldByName(refType, memberName, 
                                               LValue.STATIC);
            overloads = LValue.methodsByName(refType, memberName,
                                              LValue.STATIC);
            if ((matchingField == null) && overloads.size() == 0) {
                throw new ParseException("No static field or method with the name "
                               + memberName + " in " + refType.name());
            }
        }
        
        Value getValue() throws InvocationException, InvalidTypeException,
                                ClassNotLoadedException, IncompatibleThreadStateException,
                                ParseException {
            if (jdiValue != null) {
                return jdiValue;
            }
            if (matchingMethod == null) {
                return jdiValue = refType.getValue(matchingField);
            } else if (refType instanceof ClassType) {
                ClassType clazz = (ClassType)refType;
                return jdiValue = clazz.invokeMethod(thread, matchingMethod, methodArguments, 0);
            } else {
                throw new InvalidTypeException("Cannot invoke static method on " +
                                         refType.name());
            }
        }

        void setValue0(Value val) 
                           throws ParseException, InvalidTypeException,
                                  ClassNotLoadedException {
            if (matchingMethod != null) {
                throw new ParseException("Cannot assign to a method invocation");
            }
            if (!(refType instanceof ClassType)) {
                throw new ParseException(
                       "Cannot set interface field: " + refType);
            }
            ((ClassType)refType).setValue(matchingField, val);
            jdiValue = val;
        }

        void invokeWith(List arguments) throws ParseException {
            if (matchingMethod != null) {
                throw new ParseException("Invalid consecutive invocations");
            }
            methodArguments = arguments;
            matchingMethod = LValue.resolveOverload(overloads, arguments);
        }
    }

    private static class LValueArrayLength extends LValue {
        /*
         * Since one can code "int myLen = myArray.length;",
         * one might expect that these JDI calls would get a Value
         * object for the length of an array in the debugee:
         *    Field xxx = ArrayType.fieldByName("length")
         *    Value lenVal= ArrayReference.getValue(xxx)
         *
         * However, this doesn't work because the array length isn't
         * really stored as a field, and can't be accessed as such
         * via JDI.  Instead, the arrayRef.length() method has to be
         * used.
         */
        final ArrayReference arrayRef;
        LValueArrayLength (ArrayReference value) {
            this.arrayRef = value;
        }

        Value getValue() {
            if (jdiValue == null) {
                jdiValue = arrayRef.virtualMachine().mirrorOf(arrayRef.length());
            }
            return jdiValue;
        }

        void setValue0(Value value) throws ParseException  {
            throw new ParseException("Cannot set constant: " + value);
        }

        void invokeWith(List arguments) throws ParseException {
            throw new ParseException("Array element is not a method");
        }
    }

    private static class LValueArrayElement extends LValue {
        final ArrayReference array;
        final int index;

        LValueArrayElement(Value value, int index) throws ParseException {
            if (!(value instanceof ArrayReference)) {
                throw new ParseException(
                       "Must be array type: " + value);
            }
            this.array = (ArrayReference)value;
            this.index = index;
        }
        
        Value getValue() {
            if (jdiValue == null) {
                jdiValue = array.getValue(index);
            }
            return jdiValue;
        }

        void setValue0(Value val) throws InvalidTypeException, 
                                         ClassNotLoadedException  {
            array.setValue(index, val);
            jdiValue = val;
        }

        void invokeWith(List arguments) throws ParseException {
            throw new ParseException("Array element is not a method");
        }
    }

    private static class LValueConstant extends LValue {
        final Value value;

        LValueConstant(Value value) {
            this.value = value;
        }
        
        Value getValue() {
            if (jdiValue == null) {
                jdiValue = value;
            }
            return jdiValue;
        }

        void setValue0(Value val) throws ParseException {
            throw new ParseException("Cannot set constant: " + value);
        }

        void invokeWith(List arguments) throws ParseException {
            throw new ParseException("Constant is not a method");
        }
    }

    static LValue make(VirtualMachine vm, boolean val) {
        return new LValueConstant(vm.mirrorOf(val));
    }

    static LValue make(VirtualMachine vm, byte val) {
        return new LValueConstant(vm.mirrorOf(val));
    }

    static LValue make(VirtualMachine vm, char val) {
        return new LValueConstant(vm.mirrorOf(val));
    }

    static LValue make(VirtualMachine vm, short val) {
        return new LValueConstant(vm.mirrorOf(val));
    }

    static LValue make(VirtualMachine vm, int val) {
        return new LValueConstant(vm.mirrorOf(val));
    }

    static LValue make(VirtualMachine vm, long val) {
        return new LValueConstant(vm.mirrorOf(val));
    }

    static LValue make(VirtualMachine vm, float val) {
        return new LValueConstant(vm.mirrorOf(val));
    }

    static LValue make(VirtualMachine vm, double val) {
        return new LValueConstant(vm.mirrorOf(val));
    }

    static LValue make(VirtualMachine vm, String val) throws ParseException {
        return new LValueConstant(vm.mirrorOf(val));
    }

    static LValue makeBoolean(VirtualMachine vm, Token token) {
        return make(vm, token.image.charAt(0) == 't');
    }

    static LValue makeCharacter(VirtualMachine vm, Token token) {
        return make(vm, token.image.charAt(1));
    }

    static LValue makeFloat(VirtualMachine vm, Token token) {
        return make(vm, Float.valueOf(token.image).floatValue());
    }

    static LValue makeDouble(VirtualMachine vm, Token token) {
        return make(vm, Double.valueOf(token.image).doubleValue());
    }

    static LValue makeInteger(VirtualMachine vm, Token token) {
        return make(vm, Integer.parseInt(token.image));
    }

    static LValue makeShort(VirtualMachine vm, Token token) {
        return make(vm, Short.parseShort(token.image));
    }

    static LValue makeLong(VirtualMachine vm, Token token) {
        return make(vm, Long.parseLong(token.image));
    }

    static LValue makeByte(VirtualMachine vm, Token token) {
        return make(vm, Byte.parseByte(token.image));
    }

    static LValue makeString(VirtualMachine vm, 
                             Token token) throws ParseException {
        int len = token.image.length();
        return make(vm, token.image.substring(1,len-1));
    }

    static LValue makeNull(VirtualMachine vm, 
                           Token token) throws ParseException {
        return new LValueConstant(null);
    }

    static LValue makeThisObject(VirtualMachine vm, 
                                 ExpressionParser.GetFrame frameGetter, 
                                 Token token) throws ParseException {
        if (frameGetter == null) {
            throw new ParseException("No current thread");
        } else {
            try {
                StackFrame frame = frameGetter.get();
                ObjectReference thisObject = frame.thisObject();
                
		if (thisObject==null) {
       			throw new ParseException(
                            "No 'this'.  In native or static method");
		} else {
			return new LValueConstant(thisObject);
                }
            } catch (IncompatibleThreadStateException exc) {
                throw new ParseException("Thread not suspended");
            }
        }
    }

    static LValue makeNewObject(VirtualMachine vm, 
                                 ExpressionParser.GetFrame frameGetter, 
                                String className, List arguments) throws ParseException {
        List classes = vm.classesByName(className);
        if (classes.size() == 0) {
            throw new ParseException("No class named: " + className);
        }

        if (classes.size() > 1) {
            throw new ParseException("More than one class named: " +
                                     className);
        }
        ReferenceType refType = (ReferenceType)classes.get(0);


        if (!(refType instanceof ClassType)) {
            throw new ParseException("Cannot create instance of interface " +
                                     className);
        }

        ClassType classType = (ClassType)refType;
        List methods = new ArrayList(classType.methods()); // writable
        Iterator iter = methods.iterator();
        while (iter.hasNext()) {
            Method method = (Method)iter.next();
            if (!method.isConstructor()) {
                iter.remove();
            }
        }
        Method constructor = LValue.resolveOverload(methods, arguments);

        ObjectReference newObject;
        try {
            ThreadReference thread = frameGetter.get().thread();
            newObject = classType.newInstance(thread, constructor, arguments, 0);
        } catch (InvocationException ie) {
            throw new ParseException("Exception in " + className + " constructor: " + 
                                     ie.exception().referenceType().name());
        } catch (IncompatibleThreadStateException exc) {
            throw new ParseException("Thread not suspended");
        } catch (Exception e) {
            /*
             * TO DO: Better error handling
             */
            throw new ParseException("Unable to create " + className + " instance");
        }
	return new LValueConstant(newObject);
    }

    private static LValue nFields(LValue lval, 
                                  StringTokenizer izer,
                                  ThreadReference thread) 
                                          throws ParseException {
        if (!izer.hasMoreTokens()) {
            return lval;
        } else {
            return nFields(lval.memberLValue(izer.nextToken(), thread), izer, thread);
        }                    
    }

    static LValue makeName(VirtualMachine vm, 
                           ExpressionParser.GetFrame frameGetter, 
                           String name) throws ParseException {
        StringTokenizer izer = new StringTokenizer(name, ".");
        String first = izer.nextToken();
        // check local variables
        if (frameGetter != null) {
            try {
                StackFrame frame = frameGetter.get();
                ThreadReference thread = frame.thread();
                LocalVariable var;
                try {
                    var = frame.visibleVariableByName(first);
                } catch (AbsentInformationException e) {
                    var = null;
                }
                if (var != null) {
                    return nFields(new LValueLocal(frame, var), izer, thread);
                } else {
                    ObjectReference thisObject = frame.thisObject();
                    if (thisObject != null) {
                        // check if it is a field of 'this'
                        LValue thisLValue = new LValueConstant(thisObject);
                        LValue fv;
                        try {
                            fv = thisLValue.memberLValue(first, thread);
                        } catch (ParseException exc) {
                            fv = null;
                        }
                        if (fv != null) {
                            return nFields(fv, izer, thread);
                        }
                    }
                }
                // check for class name
                while (izer.hasMoreTokens()) {
                    List classes = vm.classesByName(first);
                    if (classes.size() > 0) {
                        if (classes.size() > 1) {
                            throw new ParseException("More than one class named: " +
                                                     first);
                        } else {
                            ReferenceType refType = (ReferenceType)classes.get(0);
                            LValue lval = new LValueStaticMember(refType, 
                                                            izer.nextToken(), thread);
                            return nFields(lval, izer, thread);
                        }
                    }
                    first = first + '.' + izer.nextToken();
                }
            } catch (IncompatibleThreadStateException exc) {
                throw new ParseException("Thread not suspended");
            }
        }
        throw new ParseException("Name unknown: " + name);
    }

    static String stringValue(LValue lval, ExpressionParser.GetFrame frameGetter
                              ) throws ParseException {
        Value val = lval.getMassagedValue(frameGetter);
        if (val == null) {
            return "null";
        }
        if (val instanceof StringReference) {
            return ((StringReference)val).value();
        }
        return val.toString();  // is this correct in all cases?
    }

    static LValue booleanOperation(VirtualMachine vm, Token token, 
                            LValue rightL, 
                            LValue leftL) throws ParseException {
        String op = token.image;
        Value right = rightL.interiorGetValue();
        Value left = leftL.interiorGetValue();
        if ( !(right instanceof PrimitiveValue) || 
             !(left instanceof PrimitiveValue) ) {
            if (op.equals("==")) {
                return make(vm, right.equals(left));
            } else if (op.equals("!=")) {
                return make(vm, !right.equals(left));
            } else {
                throw new ParseException("Operands or '" + op + 
                                     "' must be primitive");
            }
        }
        // can compare any numeric doubles
        double rr = ((PrimitiveValue)right).doubleValue();
        double ll = ((PrimitiveValue)left).doubleValue();
        boolean res;
        if (op.equals("<")) {
            res = rr < ll;
        } else if (op.equals(">")) {
            res = rr > ll;
        } else if (op.equals("<=")) {
            res = rr <= ll;
        } else if (op.equals(">=")) {
            res = rr >= ll;
        } else if (op.equals("==")) {
            res = rr == ll;
        } else if (op.equals("!=")) {
            res = rr != ll;
        } else {
            throw new ParseException("Unknown operation: " + op);
        }
        return make(vm, res);
    }                                

    static LValue operation(VirtualMachine vm, Token token, 
                            LValue rightL, LValue leftL,
                            ExpressionParser.GetFrame frameGetter
                            ) throws ParseException {
        String op = token.image;
        Value right = rightL.interiorGetValue();
        Value left = leftL.interiorGetValue();
        if ((right instanceof StringReference) ||
                              (left instanceof StringReference)) {
            if (op.equals("+")) {
                // If one is an ObjectRef, we will need to invoke
                // toString on it, so we need the thread. 
                return make(vm, stringValue(rightL, frameGetter) +
                            stringValue(leftL, frameGetter));
            }
        }
        if ((right instanceof ObjectReference) ||
                              (left instanceof ObjectReference)) {
            if (op.equals("==")) {
                return make(vm, right.equals(left));
            } else if (op.equals("!=")) {
                return make(vm, !right.equals(left));
            } else {
                throw new ParseException("Invalid operation '" +
                                         op + "' on an Object");
            }
        }
        if ((right instanceof BooleanValue) ||
                              (left instanceof BooleanValue)) {
            throw new ParseException("Invalid operation '" +
                                     op + "' on a Boolean");
        }
        // from here on, we know it is a integer kind of type
        PrimitiveValue primRight = (PrimitiveValue)right;
        PrimitiveValue primLeft = (PrimitiveValue)left;
        if ((primRight instanceof DoubleValue) ||
                              (primLeft instanceof DoubleValue)) {
            double rr = primRight.doubleValue();
            double ll = primLeft.doubleValue();
            double res;
            if (op.equals("+")) {
                res = rr + ll;
            } else if (op.equals("-")) {
                res = rr - ll;
            } else if (op.equals("*")) {
                res = rr * ll;
            } else if (op.equals("/")) {
                res = rr / ll;
            } else {
                throw new ParseException("Unknown operation: " + op);
            }
            return make(vm, res);
        }
        if ((primRight instanceof FloatValue) ||
                              (primLeft instanceof FloatValue)) {
            float rr = primRight.floatValue();
            float ll = primLeft.floatValue();
            float res;
            if (op.equals("+")) {
                res = rr + ll;
            } else if (op.equals("-")) {
                res = rr - ll;
            } else if (op.equals("*")) {
                res = rr * ll;
            } else if (op.equals("/")) {
                res = rr / ll;
            } else {
                throw new ParseException("Unknown operation: " + op);
            }
            return make(vm, res);
        }
        if ((primRight instanceof LongValue) ||
                              (primLeft instanceof LongValue)) {
            long rr = primRight.longValue();
            long ll = primLeft.longValue();
            long res;
            if (op.equals("+")) {
                res = rr + ll;
            } else if (op.equals("-")) {
                res = rr - ll;
            } else if (op.equals("*")) {
                res = rr * ll;
            } else if (op.equals("/")) {
                res = rr / ll;
            } else {
                throw new ParseException("Unknown operation: " + op);
            }
            return make(vm, res);
        } else {
            int rr = primRight.intValue();
            int ll = primLeft.intValue();
            int res;
            if (op.equals("+")) {
                res = rr + ll;
            } else if (op.equals("-")) {
                res = rr - ll;
            } else if (op.equals("*")) {
                res = rr * ll;
            } else if (op.equals("/")) {
                res = rr / ll;
            } else {
                throw new ParseException("Unknown operation: " + op);
            }
            return make(vm, res);
        }
    }   
}
