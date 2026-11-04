package com.ewallet.platform.core.provider;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Đánh dấu bean là bản giả của một provider bên thứ 3 (ngân hàng, eKYC, thông báo, AML). Bị chặn ở profile {@code prod}. */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface FakeProvider {}
