package com.gradle;

import ratpack.exec.*;
import ratpack.func.Action;
import ratpack.func.Function;
import ratpack.func.NoArgAction;
import ratpack.func.Predicate;

import java.util.concurrent.CountDownLatch;
import java.util.function.Supplier;

public class RetryablePromise<T> implements Promise<T> {

    private final Supplier<Promise<T>> promiseGenerator;
    private final int times;

    private final Promise<Promise<T>> finalPromisePromise;

    // thread safety? probably ok, we only do retries serially
    private final CountDownLatch done;
    private Promise<T> currentPromise;
    private Result<T> lastResult;
    private int currentTry;

    public RetryablePromise(Supplier<Promise<T>> promiseGenerator, int times) {
        this(ExecController.current().get().getControl(), promiseGenerator, times);
    }

    public RetryablePromise(ExecControl execControl, Supplier<Promise<T>> promiseGenerator, int times) {
        this.promiseGenerator = promiseGenerator;
        this.times = times;

        // ok, let's try subscribing to this thing
        done = new CountDownLatch(1);
        finalPromisePromise = execControl.blocking(() -> {
            System.out.println("inside final promise blocking block, about to wait");
            done.await();
            System.out.println("inside final promise blocking block, last result: " + lastResult);
            return execControl.promise((fulfiller) -> fulfiller.accept(lastResult));
        });

        // off we go
        currentTry = 0;
        System.out.println("generating first try of promise");
        currentPromise = promiseGenerator.get().cache();
//        currentPromise = promiseGenerator.get();
        currentPromise.asResult(this::handleTryResult);
    }

    private void handleTryResult(Result<T> result) {
        System.out.println("handleTryResult(" + result + ") #" + currentTry);
        lastResult = result;
        if(result.isSuccess() || ++currentTry == times) {
            // all good or run out of retries
            System.out.println("done!");
            done.countDown();
        } else {
            // retry it
            System.out.println("retry #" + currentTry);
            currentPromise = promiseGenerator.get().cache();
//            currentPromise.asResult(this::handleTryResult);
            currentPromise.asResult((subresult) -> System.out.println("** got result: " + subresult));
        }
    }

    @Override
    public SuccessPromise<T> onError(Action<? super Throwable> errorHandler) {
        return finalPromisePromise.flatMap((finalPromise) -> new DelegatingSuccessPromise<>(finalPromise.onError(errorHandler)));
    }

    @Override
    public void then(Action<? super T> callback) {
        finalPromisePromise.then((finalPromise) -> finalPromise.then(callback));
    }

    @Override
    public <O> Promise<O> map(Function<? super T, ? extends O> transformer) {
        return finalPromisePromise.flatMap((finalPromise) -> finalPromise.map(transformer));
    }

    @Override
    public <O> Promise<O> blockingMap(Function<? super T, ? extends O> transformer) {
        return finalPromisePromise.flatMap((finalPromise) -> finalPromise.blockingMap(transformer));
    }

    @Override
    public <O> Promise<O> flatMap(Function<? super T, ? extends Promise<O>> transformer) {
        return finalPromisePromise.flatMap((finalPromise) -> finalPromise.flatMap(transformer));
    }

    @Override
    public Promise<T> route(Predicate<? super T> predicate, Action<? super T> action) {
        return finalPromisePromise.flatMap((finalPromise) -> finalPromise.route(predicate, action));
    }

    @Override
    public Promise<T> onNull(NoArgAction action) {
        return finalPromisePromise.flatMap((finalPromise) -> finalPromise.onNull(action));
    }

    @Override
    public Promise<T> cache() {
        return finalPromisePromise.flatMap(PromiseOperations::cache);
    }

    @Override
    public Promise<T> defer(Action<? super Runnable> releaser) {
        return finalPromisePromise.flatMap((finalPromise) -> finalPromise.defer(releaser));
    }

    @Override
    public Promise<T> onYield(Runnable onYield) {
        return finalPromisePromise.flatMap((finalPromise) -> finalPromise.onYield(onYield));
    }

    @Override
    public Promise<T> wiretap(Action<? super Result<T>> listener) {
        return finalPromisePromise.flatMap((finalPromise) -> finalPromise.wiretap(listener));
    }

    @Override
    public Promise<T> throttled(Throttle throttle) {
        return finalPromisePromise.flatMap((finalPromise) -> finalPromise.throttled(throttle));
    }

    private static class DelegatingSuccessPromise<T> implements Promise<T> {
        private final SuccessPromise<T> delegate;

        private DelegatingSuccessPromise(SuccessPromise<T> delegate) {
            this.delegate = delegate;
        }

        @Override
        public SuccessPromise<T> onError(Action<? super Throwable> errorHandler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void then(Action<? super T> then) {
            throw new UnsupportedOperationException();

        }

        @Override
        public <O> Promise<O> map(Function<? super T, ? extends O> transformer) {
            return delegate.map(transformer);
        }

        @Override
        public <O> Promise<O> blockingMap(Function<? super T, ? extends O> transformer) {
            return delegate.blockingMap(transformer);
        }

        @Override
        public <O> Promise<O> flatMap(Function<? super T, ? extends Promise<O>> transformer) {
            return delegate.flatMap(transformer);
        }

        @Override
        public Promise<T> route(Predicate<? super T> predicate, Action<? super T> action) {
            return delegate.route(predicate, action);
        }

        @Override
        public Promise<T> onNull(NoArgAction action) {
            return delegate.onNull(action);
        }

        @Override
        public Promise<T> cache() {
            return delegate.cache();
        }

        @Override
        public Promise<T> defer(Action<? super Runnable> releaser) {
            return delegate.defer(releaser);
        }

        @Override
        public Promise<T> onYield(Runnable onYield) {
            return delegate.onYield(onYield);
        }

        @Override
        public Promise<T> wiretap(Action<? super Result<T>> listener) {
            return delegate.wiretap(listener);
        }

        @Override
        public Promise<T> throttled(Throttle throttle) {
            return delegate.throttled(throttle);
        }
    }
}
