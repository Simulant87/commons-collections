/*
 * $Header: /home/jerenkrantz/tmp/commons/commons-convert/cvs/home/cvs/jakarta-commons//collections/src/java/org/apache/commons/collections/BeanMap.java,v 1.14 2002/10/12 22:15:19 scolebourne Exp $
 * $Revision: 1.14 $
 * $Date: 2002/10/12 22:15:19 $
 *
 * ====================================================================
 *
 * The Apache Software License, Version 1.1
 *
 * Copyright (c) 1999-2002 The Apache Software Foundation.  All rights
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution, if
 *    any, must include the following acknowlegement:
 *       "This product includes software developed by the
 *        Apache Software Foundation (http://www.apache.org/)."
 *    Alternately, this acknowlegement may appear in the software itself,
 *    if and wherever such third-party acknowlegements normally appear.
 *
 * 4. The names "The Jakarta Project", "Commons", and "Apache Software
 *    Foundation" must not be used to endorse or promote products derived
 *    from this software without prior written permission. For written
 *    permission, please contact apache@apache.org.
 *
 * 5. Products derived from this software may not be called "Apache"
 *    nor may "Apache" appear in their names without prior written
 *    permission of the Apache Group.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */
package org.apache.commons.collections;

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;


/** An implementation of Map for JavaBeans which uses introspection to
  * get and put properties in the bean.
  *
  * If an exception occurs during attempts to get or set a property then the
  * property is considered non existent in the Map
  *
  * @since 1.0
  * @author <a href="mailto:jstrachan@apache.org">James Strachan</a>
  */

public class BeanMap extends AbstractMap implements Cloneable {

    private transient Object bean;

    private transient HashMap readMethods = new HashMap();
    private transient HashMap writeMethods = new HashMap();
    private transient HashMap types = new HashMap();

    /**
     *  An empty array.  Used to invoke accessors via reflection.
     */
    public static final Object[] NULL_ARGUMENTS = {};

    /**
     *  Maps primitive Class types to transformers.  The transformer
     *  transform strings into the appropriate primitive wrapper.
     */
    public static HashMap defaultTransformers = new HashMap();
    
    static {
        defaultTransformers.put( 
            Boolean.TYPE, 
            new Transformer() {
                public Object transform( Object input ) {
                    return Boolean.valueOf( input.toString() );
                }
            }
        );
        defaultTransformers.put( 
            Character.TYPE, 
            new Transformer() {
                public Object transform( Object input ) {
                    return new Character( input.toString().charAt( 0 ) );
                }
            }
        );
        defaultTransformers.put( 
            Byte.TYPE, 
            new Transformer() {
                public Object transform( Object input ) {
                    return Byte.valueOf( input.toString() );
                }
            }
        );
        defaultTransformers.put( 
            Short.TYPE, 
            new Transformer() {
                public Object transform( Object input ) {
                    return Short.valueOf( input.toString() );
                }
            }
        );
        defaultTransformers.put( 
            Integer.TYPE, 
            new Transformer() {
                public Object transform( Object input ) {
                    return Integer.valueOf( input.toString() );
                }
            }
        );
        defaultTransformers.put( 
            Long.TYPE, 
            new Transformer() {
                public Object transform( Object input ) {
                    return Long.valueOf( input.toString() );
                }
            }
        );
        defaultTransformers.put( 
            Float.TYPE, 
            new Transformer() {
                public Object transform( Object input ) {
                    return Float.valueOf( input.toString() );
                }
            }
        );
        defaultTransformers.put( 
            Double.TYPE, 
            new Transformer() {
                public Object transform( Object input ) {
                    return Double.valueOf( input.toString() );
                }
            }
        );
    }
    
    
    // Constructors
    //-------------------------------------------------------------------------

    /**
     *  Constructs a new empty <Code>BeanMap</Code>.
     */
    public BeanMap() {
    }

    /**
     *  Constructs a new <Code>BeanMap</Code> that operates on the 
     *  specified bean.  If the given bean is <Code>null</COde>, then
     *  this map will be empty.
     *
     *  @param bean  the bean for this map to operate on
     */
    public BeanMap(Object bean) {
        this.bean = bean;
        initialise();
    }

    // Map interface
    //-------------------------------------------------------------------------

    /**
     *  Clone this bean map using the following process: 
     *
     *  <ul>

     *  <li>If there is no underlying bean, return a cloned BeanMap without a
     *  bean.
     *
     *  <li>Since there is an underlying bean, try to instantiate a new bean of
     *  the same type using Class.newInstance().
     * 
     *  <li>If the instantiation fails, throw a CloneNotSupportedException
     *
     *  <li>Clone the bean map and set the newly instantiated bean as the
     *  underyling bean for the bean map.
     *
     *  <li>Copy each property that is both readable and writable from the
     *  existing object to a cloned bean map.  
     *
     *  <li>If anything fails along the way, throw a
     *  CloneNotSupportedException.
     *
     *  <ul>
     **/
    public Object clone() throws CloneNotSupportedException {
        BeanMap newMap = (BeanMap)super.clone();

        if(bean == null) {
            // no bean, just an empty bean map at the moment.  return a newly
            // cloned and empty bean map.
            return newMap;
        }

        Object newBean = null;            
        Class beanClass = null;
        try {
            beanClass = bean.getClass();
            newBean = beanClass.newInstance();
        } catch (Exception e) {
            // unable to instantiate
            throw new CloneNotSupportedException
                ("Unable to instantiate the underlying bean \"" +
                 beanClass.getName() + "\": " + e);
        }
            
        try {
            newMap.setBean(newBean);
        } catch (Exception exception) {
            throw new CloneNotSupportedException
                ("Unable to set bean in the cloned bean map: " + 
                 exception);
        }
            
        try {
            // copy only properties that are readable and writable.  If its
            // not readable, we can't get the value from the old map.  If
            // its not writable, we can't write a value into the new map.
            Iterator readableKeys = readMethods.keySet().iterator();
            while(readableKeys.hasNext()) {
                Object key = readableKeys.next();
                if(getWriteMethod(key) != null) {
                    newMap.put(key, get(key));
                }
            }
        } catch (Exception exception) {
            throw new CloneNotSupportedException
                ("Unable to copy bean values to cloned bean map: " +
                 exception);
        }

        return newMap;
    }

    /**
     *  Puts all of the writeable properties from the given BeanMap into this
     *  BeanMap.  Read-only properties will be ignored.
     *
     *  @param map  the BeanMap whose properties to put
     */
    public void putAllWriteable(BeanMap map) {
        Iterator readableKeys = map.readMethods.keySet().iterator();
        while(readableKeys.hasNext()) {
            Object key = readableKeys.next();
            if(getWriteMethod(key) != null) {
                this.put(key, map.get(key));
            }
        }
    }


    /**
     *  This method reinitializes the bean map to have default values for the
     *  bean's properties.  This is accomplished by constructing a new instance
     *  of the bean which the map uses as its underlying data source.  This
     *  behavior for <code>clear()</code> differs from the Map contract in that
     *  the mappings are not actually removed from the map (the mappings for a
     *  BeanMap are fixed).
     **/
    public void clear() {
        if(bean == null) return;

        Class beanClass = null;
        try {
            beanClass = bean.getClass();
            bean = beanClass.newInstance();
        }
        catch (Exception e) {
            throw new UnsupportedOperationException( "Could not create new instance of class: " + beanClass );
        }
    }

    /**
     *  Returns true if the bean defines a property with the given name.
     *  The given name must be a <Code>String</Code>; if not, this method
     *  returns false.  This method will also return false if the bean
     *  does not define a property with that name.
     *
     *  @param name  the name of the property to check
     *  @return false if the given name is null or is not a <Code>String</Code>;
     *    false if the bean does not define a property with that name; or
     *    true if the bean does define a property with that name
     */
    public boolean containsKey(Object name) {
        Method method = getReadMethod( name );
        return method != null;
    }

    /**
     *  Returns true if the bean defines a property whose current value is
     *  the given object.
     *
     *  @param value  the value to check
     *  @return false  true if the bean has at least one property whose 
     *    current value is that object, false otherwise
     */
    public boolean containsValue(Object value) {
        // use default implementation
        return super.containsValue( value );
    }

    /**
     *  Returns the value of the bean's property with the given name.
     *  The given name must be a {@link String} and must not be 
     *  null; otherwise, this method returns <Code>null</Code>.
     *  If the bean defines a property with the given name, the value of
     *  that property is returned.  Otherwise, <Code>null</Code> is 
     *  returned.
     *
     *  @param name  the name of the property whose value to return
     *  @return  the value of the property with that name
     */
    public Object get(Object name) {
        if ( bean != null ) {
            Method method = getReadMethod( name );
            if ( method != null ) {
                try {
                    return method.invoke( bean, NULL_ARGUMENTS );
                }
                catch (  IllegalAccessException e ) {
                    logWarn( e );
                }
                catch ( IllegalArgumentException e ) {
                    logWarn(  e );
                }
                catch ( InvocationTargetException e ) {
                    logWarn(  e );
                }
                catch ( NullPointerException e ) {
                    logWarn(  e );
                }
            }
        }
        return null;
    }

    /**
     *  Sets the bean property with the given name to the given value.
     *
     *  @param name  the name of the property to set
     *  @param value  the value to set that property to
     *  @return  the previous value of that property
     *  @throws IllegalArgumentException  if the given name is null;
     *    if the given name is not a {@link String}; if the bean doesn't
     *    define a property with that name; or if the bean property with
     *    that name is read-only
     */
    public Object put(Object name, Object value) throws IllegalArgumentException, ClassCastException {
        if ( bean != null ) {
            Object oldValue = get( name );
            Method method = getWriteMethod( name );
            if ( method == null ) {
                throw new IllegalArgumentException( "The bean of type: "+ bean.getClass().getName() + " has no property called: " + name );
            }
            try {
                Object[] arguments = createWriteMethodArguments( method, value );
                method.invoke( bean, arguments );

                Object newValue = get( name );
                firePropertyChange( name, oldValue, newValue );
            }
            catch ( InvocationTargetException e ) {
                logInfo( e );
                throw new IllegalArgumentException( e.getMessage() );
            }
            catch ( IllegalAccessException e ) {
                logInfo( e );
                throw new IllegalArgumentException( e.getMessage() );
            }
            return oldValue;
        }
        return null;
    }
                    
    /**
     *  Returns the number of properties defined by the bean.
     *
     *  @return  the number of properties defined by the bean
     */
    public int size() {
        return readMethods.size();
    }

    
    /**
     * Get the keys for this BeanMap.
     * 
     * @return BeanMap keys.  The Set returned by this method is not
     *         modifiable.
     */
    public Set keySet() {
        return Collections.unmodifiableSet(readMethods.keySet());
    }

    /**
     * Get the mappings for this BeanMap
     * 
     * @return BeanMap mappings.  The Set returned by this method
     *         is not modifiable.
     */
    public Set entrySet() {
        return Collections.unmodifiableSet(new AbstractSet() {
            public Iterator iterator() {
                return new Iterator() {

                    Iterator methodIter = 
                      BeanMap.this.readMethods.keySet().iterator();

                    public boolean hasNext() {
                        return methodIter.hasNext();
                    }

                    public Object next() {
                        Object key = (Object)methodIter.next();
                        return new DefaultMapEntry(key, get(key));
                    }

                    public void remove() {
                      throw new UnsupportedOperationException
                        ("remove() not supported from BeanMap.entrySet()");
                    }
                };
            }

            public int size() {
              return BeanMap.this.readMethods.size();
            }
        });
    }

    /**
     * Returns the values for the BeanMap.
     * 
     * @return values for the BeanMap.  The returned collection is not
     *         modifiable.
     */
    public Collection values() {
        ArrayList answer = new ArrayList( readMethods.size() );
        for ( Iterator iter = valueIterator(); iter.hasNext(); ) {
            answer.add( iter.next() );
        }
        return Collections.unmodifiableList(answer);
    }


    // Helper methods
    //-------------------------------------------------------------------------

    /**
     * Returns the type of the property with the given name.
     *
     * @param name  the name of the property
     * @return  the type of the property, or <Code>null</Code> if no such
     *   property exists
     */
    public Class getType(String name) {
        return (Class) types.get( name );
    }

    /**
     *  Convenience method for getting an iterator over the keys.
     *
     *  @return an iterator over the keys
     */
    public Iterator keyIterator() {
        return readMethods.keySet().iterator();
    }

    /**
     *  Convenience method for getting an iterator over the values.
     *
     *  @return an iterator over the values
     */
    public Iterator valueIterator() {
        final Iterator iter = keyIterator();
        return new Iterator() {            
            public boolean hasNext() {
                return iter.hasNext();
            }
            public Object next() {
                Object key = iter.next();
                return get( (String) key );
            }
            public void remove() {
                throw new UnsupportedOperationException( "remove() not supported for BeanMap" );
            }
        };
    }

    /**
     *  Convenience method for getting an iterator over the entries.
     *
     *  @return an iterator over the entries
     */
    public Iterator entryIterator() {
        final Iterator iter = keyIterator();
        return new Iterator() {            
            public boolean hasNext() {
                return iter.hasNext();
            }            
            public Object next() {
                Object key = iter.next();
                Object value = get( (String) key );
                return new MyMapEntry( BeanMap.this, key, value );
            }            
            public void remove() {
                throw new UnsupportedOperationException( "remove() not supported for BeanMap" );
            }
        };
    }


    // Properties
    //-------------------------------------------------------------------------

    /**
     *  Returns the bean currently being operated on.  The return value may
     *  be null if this map is empty.
     *
     *  @return the bean being operated on by this map
     */
    public Object getBean() {
        return bean;
    }

    /**
     *  Sets the bean to be operated on by this map.  The given value may
     *  be null, in which case this map will be empty.
     *
     *  @param newBean  the new bean to operate on
     */
    public void setBean( Object newBean ) {
        bean = newBean;
        reinitialise();
    }


    // Implementation methods
    //-------------------------------------------------------------------------

    /**
     *  Returns the accessor for the property with the given name.
     *
     *  @param name  the name of the property 
     *  @return null if the name is null; null if the name is not a 
     *  {@link String}; null if no such property exists; or the accessor
     *   method for that property
     */
    protected Method getReadMethod( Object name ) {
        return (Method) readMethods.get( name );
    }

    /**
     *  Returns the mutator for the property with the given name.
     *
     *  @param name  the name of the 
     *  @return null if the name is null; null if the name is not a 
     *  {@link String}; null if no such property exists; null if the 
     *  property is read-only; or the mutator method for that property
     */
    protected Method getWriteMethod( Object name ) {
        return (Method) writeMethods.get( name );
    }

    /**
     *  Reinitializes this bean.  Called during {@link #setBean(Object)}.
     *  Does introspection to find properties.
     */
    protected void reinitialise() {
        readMethods.clear();
        writeMethods.clear();
        types.clear();
        initialise();
    }

    private void initialise() {
        if(getBean() == null) return;

        Class  beanClass = getBean().getClass();
        try {
            //BeanInfo beanInfo = Introspector.getBeanInfo( bean, null );
            BeanInfo beanInfo = Introspector.getBeanInfo( beanClass );
            PropertyDescriptor[] propertyDescriptors = beanInfo.getPropertyDescriptors();
            if ( propertyDescriptors != null ) {
                for ( int i = 0; i < propertyDescriptors.length; i++ ) {
                    PropertyDescriptor propertyDescriptor = propertyDescriptors[i];
                    if ( propertyDescriptor != null ) {
                        String name = propertyDescriptor.getName();
                        Method readMethod = propertyDescriptor.getReadMethod();
                        Method writeMethod = propertyDescriptor.getWriteMethod();
                        Class aType = propertyDescriptor.getPropertyType();

                        if ( readMethod != null ) {
                            readMethods.put( name, readMethod );
                        }
                        if ( writeMethods != null ) {
                            writeMethods.put( name, writeMethod );
                        }
                        types.put( name, aType );
                    }
                }
            }
        }
        catch ( IntrospectionException e ) {
            logWarn(  e );
        }
    }

    /**
     *  Called during a successful {@link #put(Object,Object)} operation.
     *  Default implementation does nothing.  Override to be notified of
     *  property changes in the bean caused by this map.
     *
     *  @param key  the name of the property that changed
     *  @param oldValue  the old value for that property
     *  @param newValue  the new value for that property
     */
    protected void firePropertyChange( Object key, Object oldValue, Object newValue ) {
    }

    // Implementation classes
    //-------------------------------------------------------------------------

    /**
     *  Map entry used by {@link BeanMap}.
     */
    protected static class MyMapEntry extends DefaultMapEntry {        
        private BeanMap owner;
        
        /**
         *  Constructs a new <Code>MyMapEntry</Code>.
         *
         *  @param owner  the BeanMap this entry belongs to
         *  @param key  the key for this entry
         *  @param value  the value for this entry
         */
        protected MyMapEntry( BeanMap owner, Object key, Object value ) {
            super( key, value );
            this.owner = owner;
        }

        /**
         *  Sets the value.
         *
         *  @param value  the new value for the entry
         *  @return the old value for the entry
         */
        public Object setValue(Object value) {
            Object key = getKey();
            Object oldValue = owner.get( key );

            owner.put( key, value );
            Object newValue = owner.get( key );
            super.setValue( newValue );
            return oldValue;
        }
    }

    /**
     *  Creates an array of parameters to pass to the given mutator method.
     *  If the given object is not the right type to pass to the method 
     *  directly, it will be converted using {@link #convertType(Class,Object)}.
     *
     *  @param method  the mutator method
     *  @param value  the value to pass to the mutator method
     *  @return an array containing one object that is either the given value
     *    or a transformed value
     *  @throws IllegalAccessException if {@link #convertType(Class,Object)}
     *    raises it
     *  @throws IllegalArgumentException if any other exception is raised
     *    by {@link #convertType(Class,Object)}
     */
    protected Object[] createWriteMethodArguments( Method method, Object value ) throws IllegalAccessException, ClassCastException {            
        try {
            if ( value != null ) {
                Class[] types = method.getParameterTypes();
                if ( types != null && types.length > 0 ) {
                    Class paramType = types[0];
                    if ( ! paramType.isAssignableFrom( value.getClass() ) ) {
                        value = convertType( paramType, value );
                    }
                }
            }
            Object[] answer = { value };
            return answer;
        }
        catch ( InvocationTargetException e ) {
            logInfo( e );
            throw new IllegalArgumentException( e.getMessage() );
        }
        catch ( InstantiationException e ) {
            logInfo( e );
            throw new IllegalArgumentException( e.getMessage() );
        }
    }

    /**
     * Converts the given value to the given type.  First, reflection is
     * is used to find a public constructor declared by the given class 
     * that takes one argument, which must be the precise type of the 
     * given value.  If such a constructor is found, a new object is
     * created by passing the given value to that constructor, and the
     * newly constructed object is returned.<P>
     *
     * If no such constructor exists, and the given type is a primitive
     * type, then the given value is converted to a string using its 
     * {@link Object#toString() toString()} method, and that string is
     * parsed into the correct primitve type using, for instance, 
     * {@link Integer#valueOf(String)} to convert the string into an
     * <Code>int</Code>.<P>
     *
     * If no special constructor exists and the given type is not a 
     * primitive type, this method returns the original value.
     *
     * @param newType  the type to convert the value to
     * @param value  the value to conert
     * @return the converted value
     * @throws NumberFormatException if newType is a primitive type, and 
     *   the string representation of the given value cannot be converted
     *   to that type
     * @throws InstantiationException  if the constructor found with 
     *   reflection raises it
     * @throws InvocationTargetExcetpion  if the constructor found with
     *   reflection raises it
     * @throws IllegalAccessException  never
     * @throws IllegalArgumentException  never
     */
    protected Object convertType( Class newType, Object value ) 
        throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        
        // try call constructor
        Class[] types = { value.getClass() };
        try {
            Constructor constructor = newType.getConstructor( types );        
            Object[] arguments = { value };
            return constructor.newInstance( arguments );
        }
        catch ( NoSuchMethodException e ) {
            // try using the transformers
            Transformer transformer = getTypeTransformer( newType );
            if ( transformer != null ) {
                return transformer.transform( value );
            }
            return value;
        }
    }

    /**
     *  Returns a transformer for the given primitive type.
     *
     *  @param aType  the primitive type whose transformer to return
     *  @return a transformer that will convert strings into that type,
     *   or null if the given type is not a primitive type
     */
    protected Transformer getTypeTransformer( Class aType ) {
        return (Transformer) defaultTransformers.get( aType );
    }

    /**
     *  Logs the given exception to <Code>System.out</Code>.  Used to display
     *  warnings while accessing/mutating the bean.
     *
     *  @param e  the exception to log
     */
    protected void logInfo(Exception e) {
        // XXXX: should probably use log4j here instead...
        System.out.println( "INFO: Exception: " + e );
    }

    /**
     *  Logs the given exception to <Code>System.err</Code>.  Used to display
     *  errors while accessing/mutating the bean.
     *
     *  @param e  the exception to log
     */
    protected void logWarn(Exception e) {
        // XXXX: should probably use log4j here instead...
        System.out.println( "WARN: Exception: " + e );
        e.printStackTrace();
    }
}

