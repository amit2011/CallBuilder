/*
 * Copyright (C) 2015 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.callbuilder;

import static com.google.common.truth.Truth.assertThat;

import com.google.callbuilder.CallBuilder;
import com.google.callbuilder.style.ImmutableListAdding;
import com.google.callbuilder.style.OptionalSetting;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RunWith(JUnit4.class)
public class CallBuilderTest {
  static class ConfusingSignatures {
    int addOffset;

    @CallBuilder(className = "NonStaticAdder")
    int add(int x, int y, int z) {
      return addOffset + x + y + z;
    }

    @CallBuilder(className = "StaticConcater")
    static String add(String left, String right) {
      return left + right;
    }

    @CallBuilder(className = "HasGenericsContainsCheck")
    static boolean checkContains(Map<String, Integer> map, String key, int value) {
      return Integer.valueOf(value).equals(map.get(key));
    }

    @CallBuilder
    static Map<String, String> singletonMap(String key, String value) {
      return ImmutableMap.of(key, value);
    }
  }

  static class HasGen<E> {
    int which;

    @CallBuilder
    E pickSome(E first, E second, E third) {
      switch (which) {
        case 0:
          return first;
        case 1:
          return second;
        default:
          return third;
      }
    }

    @CallBuilder
    static int pickSecond(int first, int second, int third) {
      return second;
    }

    @CallBuilder(className = "ThreeInIterableBuilder")
    <F extends E> Iterable<E> toIterable(F first, F second, F third) {
      return ImmutableList.<E>of(first, second, third);
    }

    @CallBuilder(className = "LazyAppender", methodName = "append")
    static <F> Iterable<F> lazyAppend(Iterable<F> allButLast, F last) {
      return Iterables.concat(allButLast, ImmutableList.of(last));
    }
  }

  static class Container<I> {
    final I item;

    @CallBuilder
    Container(I item) {
      this.item = item;
    }
  }

  static class Name {
    String family;
    String given;

    @CallBuilder
    Name(String family, String given) {
      this.family = Preconditions.checkNotNull(family);
      this.given = Preconditions.checkNotNull(given);
    }
  }

  static class TwoImmutableLists {
    ImmutableList<String> first;
    ImmutableList<Integer> second;

    @CallBuilder
    TwoImmutableLists(
        @BuilderField(style = ImmutableListAdding.class) ImmutableList<String> first,
        @BuilderField(style = ImmutableListAdding.class) ImmutableList<Integer> second) {
      this.first = first;
      this.second = second;
    }
  }

  static class HasOptionals {
    Optional<Integer> age;
    Optional<Integer> income;

    @CallBuilder
    HasOptionals(
        @BuilderField(style = OptionalSetting.class) Optional<Integer> age,
        @BuilderField(style = OptionalSetting.class) Optional<Integer> income) {
      this.age = age;
      this.income = income;
    }
  }

  @Test
  public void nonStaticMethod() {
    ConfusingSignatures signatures = new ConfusingSignatures();
    signatures.addOffset = 1000;
    int result = new NonStaticAdder(signatures)
        .setX(100)
        .setY(10)
        .setZ(1)
        .build();
    Assert.assertEquals(1111, result);
  }

  @Test
  public void staticMethod() {
    Assert.assertEquals(
        "foobar",
        new StaticConcater().setLeft("foo").setRight("bar").build());
  }

  @Test
  public void genericsInParameters() {
    ImmutableMap<String, Integer> map = ImmutableMap.of("foo", 999, "bar", 1000);
    Assert.assertEquals(false, new HasGenericsContainsCheck()
        .setMap(map)
        .setKey("foo")
        .setValue(888)
        .build());
    Assert.assertEquals(true, new HasGenericsContainsCheck()
        .setMap(map)
        .setKey("foo")
        .setValue(999)
        .build());
  }

  @Test
  public void returnIsGeneric() {
    Assert.assertEquals(ImmutableMap.of("a", "b"),
        new SingletonMapBuilder().setKey("a").setValue("b").build());
  }

  @Test
  public void genericEnclosingClassButMethodIsStatic() {
    Assert.assertEquals(101,
        new PickSecondBuilder()
            .setFirst(99)
            .setSecond(101)
            .setThird(8)
            .build());
  }

  @Test
  public void genericEnclosingClass() {
    HasGen<Map<String, String>> hasGen = new HasGen<>();
    hasGen.which = 1;
    Assert.assertEquals(
        ImmutableMap.of("a", "b"),
        new PickSomeBuilder<Map<String, String>>(hasGen)
            .setSecond(ImmutableMap.of("a", "b"))
            .build());
  }

  @Test
  public void differentGenericArgsOnEnclosingClassAndMethod() {
    HasGen<List<Integer>> hasGen = new HasGen<>();
    ArrayList<Integer> first = new ArrayList<>();
    ArrayList<Integer> second = new ArrayList<>();
    ArrayList<Integer> third = new ArrayList<>();

    Iterable<List<Integer>> result =
        new ThreeInIterableBuilder<List<Integer>, ArrayList<Integer>>(hasGen)
            .setFirst(first)
            .setSecond(second)
            .setThird(third)
            .build();
    Assert.assertSame(first, Iterables.get(result, 0));
    Assert.assertSame(second, Iterables.get(result, 1));
    Assert.assertSame(third, Iterables.get(result, 2));
  }

  @Test
  public void genericArgsOnMethodAndContainingClassButMethodIsStatic() {
    Iterable<Integer> numbers = new LazyAppender<Integer>()
        .setAllButLast(ImmutableList.of(1, 2))
        .setLast(3)
        .append();

    assertThat(numbers)
        .containsExactly(1, 2, 3)
        .inOrder();
  }

  @Test
  public void builderForConstructorOnGenericClass() {
    Container<String> c = new ContainerBuilder<String>()
        .setItem(":)")
        .build();
    Assert.assertEquals(":)", c.item);
  }

  @Test
  public void builderForConstructorOnNonGenericClass() {
    Name n = new NameBuilder().setGiven("Eric").setFamily("Schmidt").build();
    Assert.assertEquals("Eric", n.given);
    Assert.assertEquals("Schmidt", n.family);
  }

  @Test
  public void testCustomFieldStyle() {
    TwoImmutableLists lists = new TwoImmutableListsBuilder()
        .addToFirst("one")
        .addToSecond(1)
        .addToFirst("DOS")
        .addToSecond(2)
        .addAllToFirst(ImmutableList.of("san", "ourfay", "FIVE"))
        .addAllToSecond(ImmutableList.of(3, 4, 5))
        .build();
    Assert.assertEquals(
        ImmutableList.of("one", "DOS", "san", "ourfay", "FIVE"),
        lists.first);
    Assert.assertEquals(
        ImmutableList.of(1, 2, 3, 4, 5),
        lists.second);
  }

  @Test
  public void optionalSettingFieldStyle() {
    HasOptionals bothEmpty = new HasOptionalsBuilder()
        .build();
    Assert.assertEquals(Optional.absent(), bothEmpty.age);
    Assert.assertEquals(Optional.absent(), bothEmpty.income);

    HasOptionals ageOnly = new HasOptionalsBuilder()
        .setAge(14)
        .build();
    Assert.assertEquals(Optional.of(14), ageOnly.age);
    Assert.assertEquals(Optional.absent(), ageOnly.income);

    HasOptionals incomeOnly = new HasOptionalsBuilder()
        .setIncome(1000)
        .build();
    Assert.assertEquals(Optional.absent(), incomeOnly.age);
    Assert.assertEquals(Optional.of(1000), incomeOnly.income);
  }
}
