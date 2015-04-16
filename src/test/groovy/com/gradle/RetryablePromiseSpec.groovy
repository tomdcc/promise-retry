package com.gradle

import ratpack.exec.ExecControl
import ratpack.exec.Promise
import ratpack.test.exec.ExecHarness
import spock.lang.Specification

import java.util.function.Supplier

class RetryablePromiseSpec extends Specification {
    ExecHarness harness

    def setup() {
        harness = ExecHarness.harness()
    }

    void cleanup() {
        harness.close()
    }

    def test() {
        given:
        def resultObject = new Object()
        int tryNumber = 0
        ExecControl control
        Supplier<Promise<Object>> promiseFactory = {
            println("promise generation #$tryNumber")
            control.promise { fulfiller ->
                println("inside nested promise")
                if(tryNumber++ < 3) {
                    println("returning error")
                    fulfiller.error(new RuntimeException())
                } else {
                    println("returning success")
                    fulfiller.success(resultObject)
                }
            }
        }

        when:
        def result = harness.yield { execution ->
            control = execution.controller.control
            new RetryablePromise(control, promiseFactory, 5)
        }

        then:
        result.value == resultObject
    }
}
