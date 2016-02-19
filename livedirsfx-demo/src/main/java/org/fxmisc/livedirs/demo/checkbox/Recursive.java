package org.fxmisc.livedirs.demo.checkbox;

/**
 * Allows recursive {@link FunctionalInterface} calls.
 *
 * <p>See https://stackoverflow.com/questions/19429667/implement-recursive-lambda-function-using-java-8</p>
 *
 * @param <I> the {@link FunctionalInterface} to call recursively.
 */
public class Recursive<I> {

    public I f;
}
