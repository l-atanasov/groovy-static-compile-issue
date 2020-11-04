package com.example.groovycompiler;

import static org.codehaus.groovy.ast.tools.GeneralUtils.args;
import static org.codehaus.groovy.ast.tools.GeneralUtils.block;
import static org.codehaus.groovy.ast.tools.GeneralUtils.callX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.constX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.ctorX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.declS;
import static org.codehaus.groovy.ast.tools.GeneralUtils.returnS;
import static org.codehaus.groovy.ast.tools.GeneralUtils.varX;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.List;

import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.CompileUnit;
import org.codehaus.groovy.ast.InnerClassNode;
import org.codehaus.groovy.ast.ModuleNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.ErrorCollector;
import org.codehaus.groovy.control.Phases;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.control.customizers.ASTTransformationCustomizer;
import org.codehaus.groovy.tools.GroovyClass;
import org.junit.Test;

import groovy.lang.GroovyClassLoader;
import groovy.transform.CompileStatic;
import groovyjarjarasm.asm.Opcodes;

public class ChainedMethodCallTest
{

    @Test
    public void testDynamicallyBuiltTopLevelClass() throws Exception
    {
        ClassNode topLevelClass = ClassHelper.make("com.example.groovycompiler.test.TopLevelClass");
        ClassNode stringBuilderClass = ClassHelper.make(StringBuilder.class);
        topLevelClass.addMethod("test", Opcodes.ACC_PUBLIC, stringBuilderClass, Parameter.EMPTY_ARRAY, ClassNode.EMPTY_ARRAY, block(
                declS(varX("sb", stringBuilderClass), callX(ctorX(stringBuilderClass), "append", args(constX("testString")))),
                returnS(varX("sb", stringBuilderClass))
        ));
        
        CompilerConfiguration config = new CompilerConfiguration();
        config.addCompilationCustomizers(new ASTTransformationCustomizer(CompileStatic.class));
        GroovyClassLoader classLoader = new GroovyClassLoader(Thread.currentThread().getContextClassLoader(), config);
        compile(classLoader, config, topLevelClass);
        
        Class<?> compiledClass = Class.forName("com.example.groovycompiler.test.TopLevelClass", true, classLoader);
        Object instance = compiledClass.newInstance();
        StringBuilder result = (StringBuilder) compiledClass.getMethod("test").invoke(instance);
        assertNotNull(result);
        assertEquals("testString", result.toString());
    }
    
    @Test
    public void testDynamicallyBuiltNestedClass() throws Exception
    {
        ClassNode topLevelClass = ClassHelper.make("com.example.groovycompiler.test.TopLevelClass");
        InnerClassNode nestedClass = new InnerClassNode(topLevelClass, "com.example.groovycompiler.test.TopLevelClass$NestedClass", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, ClassHelper.OBJECT_TYPE);
        ClassNode stringBuilderClass = ClassHelper.make(StringBuilder.class);
        nestedClass.addMethod("test", Opcodes.ACC_PUBLIC, stringBuilderClass, Parameter.EMPTY_ARRAY, ClassNode.EMPTY_ARRAY, block(
                declS(varX("sb", stringBuilderClass), callX(ctorX(stringBuilderClass), "append", args(constX("testString")))),
                returnS(varX("sb", stringBuilderClass))
        ));
        
        CompilerConfiguration config = new CompilerConfiguration();
        config.addCompilationCustomizers(new ASTTransformationCustomizer(CompileStatic.class));
        GroovyClassLoader classLoader = new GroovyClassLoader(Thread.currentThread().getContextClassLoader(), config);
        compile(classLoader, config, topLevelClass, nestedClass);
        
        Class<?> compiledClass = Class.forName("com.example.groovycompiler.test.TopLevelClass$NestedClass", true, classLoader);
        Object instance = compiledClass.newInstance();
        StringBuilder result = (StringBuilder) compiledClass.getMethod("test").invoke(instance);
        assertNotNull(result);
        assertEquals("testString", result.toString());
    }
    
    private void compile(GroovyClassLoader classLoader, CompilerConfiguration config, ClassNode... classes)
    {
        CompilationUnit compilationUnit = new SourcelessCompilationUnit(config, classLoader);
        
        for (ClassNode classNode : classes)
        {
            compilationUnit.addClassNode(classNode);
        }
        
        compilationUnit.compile(Phases.CLASS_GENERATION);
        
        List<GroovyClass> generatedClasses = compilationUnit.getClasses();
        for (GroovyClass groovyClass : generatedClasses)
        {
            classLoader.defineClass(groovyClass.getName(), groovyClass.getBytes());
        }
    }
    
    public static class SourcelessCompilationUnit extends CompilationUnit
    {

        public SourcelessCompilationUnit(CompilerConfiguration config, GroovyClassLoader classLoader)
        {
            super(config, null, classLoader);
        }
        
        @Override
        public void addClassNode(ClassNode node)
        {
            ModuleNode module = new SourcelessModuleNode(node.getName(), getAST());
            getAST().addModule(module);
            module.addClass(node);
        }
    }
    
    public static class SourcelessModuleNode extends ModuleNode
    {

        private final SourceUnit sourceUnit;

        public SourcelessModuleNode(String className, CompileUnit unit)
        {
            super(unit);
            this.sourceUnit = new SourceUnit(className, (String) null, unit.getConfig(), unit.getClassLoader(),
                    new ErrorCollector(unit.getConfig()));
        }

        @Override
        public SourceUnit getContext()
        {
            return sourceUnit;
        }
    }
}
