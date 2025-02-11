/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.table.types.inference;

import org.apache.flink.table.api.DataTypes;
import org.apache.flink.table.types.DataType;
import org.apache.flink.table.types.inference.strategies.SpecificInputTypeStrategies;
import org.apache.flink.table.types.logical.LocalZonedTimestampType;
import org.apache.flink.table.types.logical.LogicalTypeFamily;
import org.apache.flink.table.types.logical.LogicalTypeRoot;
import org.apache.flink.table.types.logical.TimestampKind;
import org.apache.flink.table.types.utils.TypeConversions;

import java.math.BigDecimal;
import java.util.stream.Stream;

import static org.apache.flink.table.types.inference.InputTypeStrategies.ANY;
import static org.apache.flink.table.types.inference.InputTypeStrategies.LITERAL;
import static org.apache.flink.table.types.inference.InputTypeStrategies.LITERAL_OR_NULL;
import static org.apache.flink.table.types.inference.InputTypeStrategies.OUTPUT_IF_NULL;
import static org.apache.flink.table.types.inference.InputTypeStrategies.WILDCARD;
import static org.apache.flink.table.types.inference.InputTypeStrategies.and;
import static org.apache.flink.table.types.inference.InputTypeStrategies.constraint;
import static org.apache.flink.table.types.inference.InputTypeStrategies.explicit;
import static org.apache.flink.table.types.inference.InputTypeStrategies.explicitSequence;
import static org.apache.flink.table.types.inference.InputTypeStrategies.logical;
import static org.apache.flink.table.types.inference.InputTypeStrategies.or;
import static org.apache.flink.table.types.inference.InputTypeStrategies.sequence;
import static org.apache.flink.table.types.inference.InputTypeStrategies.varyingSequence;
import static org.apache.flink.table.types.inference.strategies.SpecificInputTypeStrategies.INDEX;
import static org.apache.flink.table.types.inference.strategies.SpecificInputTypeStrategies.percentage;
import static org.apache.flink.table.types.inference.strategies.SpecificInputTypeStrategies.percentageArray;

/** Tests for built-in {@link InputTypeStrategies}. */
class InputTypeStrategiesTest extends InputTypeStrategiesTestBase {

    @Override
    protected Stream<TestSpec> testData() {
        return Stream.of(
                // wildcard with 2 arguments
                TestSpec.forStrategy(WILDCARD)
                        .calledWithArgumentTypes(DataTypes.INT(), DataTypes.INT())
                        .expectSignature("f(*)")
                        .expectArgumentTypes(DataTypes.INT(), DataTypes.INT()),

                // wildcard with 0 arguments
                TestSpec.forStrategy(WILDCARD)
                        .calledWithArgumentTypes()
                        .expectSignature("f(*)")
                        .expectArgumentTypes(),

                // explicit sequence
                TestSpec.forStrategy(
                                explicitSequence(
                                        DataTypes.INT().bridgedTo(int.class), DataTypes.BOOLEAN()))
                        .calledWithArgumentTypes(DataTypes.INT(), DataTypes.BOOLEAN())
                        .expectSignature("f(INT, BOOLEAN)")
                        .expectArgumentTypes(
                                DataTypes.INT().bridgedTo(int.class), DataTypes.BOOLEAN()),

                // explicit sequence with ROW ignoring field names
                TestSpec.forStrategy(
                                explicitSequence(
                                        DataTypes.ROW(
                                                DataTypes.FIELD("expected", DataTypes.INT()))))
                        .calledWithArgumentTypes(
                                DataTypes.ROW(DataTypes.FIELD("actual", DataTypes.INT())))
                        .expectSignature("f(ROW<`expected` INT>)")
                        .expectArgumentTypes(
                                DataTypes.ROW(DataTypes.FIELD("expected", DataTypes.INT()))),

                // invalid named sequence
                TestSpec.forStrategy(
                                explicitSequence(
                                        new String[] {"i", "s"},
                                        new DataType[] {DataTypes.INT(), DataTypes.STRING()}))
                        .calledWithArgumentTypes(DataTypes.INT())
                        .expectErrorMessage(
                                "Invalid input arguments. Expected signatures are:\nf(i INT, s STRING)"),

                // incompatible nullability
                TestSpec.forStrategy(explicitSequence(DataTypes.BIGINT().notNull()))
                        .calledWithArgumentTypes(DataTypes.BIGINT())
                        .expectErrorMessage(
                                "Unsupported argument type. Expected type 'BIGINT NOT NULL' but actual type was 'BIGINT'."),

                // implicit cast
                TestSpec.forStrategy(explicitSequence(DataTypes.BIGINT()))
                        .calledWithArgumentTypes(DataTypes.INT())
                        .expectArgumentTypes(DataTypes.BIGINT()),

                // incompatible types
                TestSpec.forStrategy(explicitSequence(DataTypes.BIGINT()))
                        .calledWithArgumentTypes(DataTypes.STRING())
                        .expectErrorMessage(
                                "Unsupported argument type. Expected type 'BIGINT' but actual type was 'STRING'."),

                // incompatible number of arguments
                TestSpec.forStrategy(explicitSequence(DataTypes.BIGINT(), DataTypes.BIGINT()))
                        .calledWithArgumentTypes(DataTypes.BIGINT())
                        .expectErrorMessage(
                                "Invalid number of arguments. At least 2 arguments expected but 1 passed."),

                // any type
                TestSpec.forStrategy(sequence(ANY))
                        .calledWithArgumentTypes(DataTypes.BIGINT())
                        .expectSignature("f(<ANY>)")
                        .expectArgumentTypes(DataTypes.BIGINT()),

                // incompatible number of arguments
                TestSpec.forStrategy(sequence(ANY))
                        .calledWithArgumentTypes(DataTypes.BIGINT(), DataTypes.BIGINT())
                        .expectErrorMessage(
                                "Invalid number of arguments. At most 1 arguments expected but 2 passed."),
                TestSpec.forStrategy(
                                "OR with bridging class",
                                or(
                                        explicitSequence(DataTypes.STRING()),
                                        explicitSequence(DataTypes.INT().bridgedTo(int.class)),
                                        explicitSequence(DataTypes.BOOLEAN())))
                        .calledWithArgumentTypes(DataTypes.INT())
                        .calledWithArgumentTypes(DataTypes.TINYINT())
                        .expectSignature("f(STRING)\nf(INT)\nf(BOOLEAN)")
                        .expectArgumentTypes(DataTypes.INT().bridgedTo(int.class)),
                TestSpec.forStrategy(
                                "OR with implicit casting",
                                or(
                                        explicitSequence(DataTypes.TINYINT()),
                                        explicitSequence(DataTypes.INT()),
                                        explicitSequence(DataTypes.BIGINT())))
                        .calledWithArgumentTypes(DataTypes.SMALLINT())
                        .expectArgumentTypes(DataTypes.INT()),
                TestSpec.forStrategy(
                                "OR with implicit casting of null",
                                or(
                                        explicitSequence(DataTypes.STRING().notNull()),
                                        explicitSequence(DataTypes.INT().notNull()),
                                        explicitSequence(DataTypes.BIGINT())))
                        .calledWithArgumentTypes(DataTypes.NULL())
                        .expectArgumentTypes(DataTypes.BIGINT()),
                TestSpec.forStrategy(
                                "OR with implicit casting using first match",
                                or(
                                        explicitSequence(DataTypes.VARCHAR(20)),
                                        explicitSequence(DataTypes.VARCHAR(10))))
                        .calledWithArgumentTypes(DataTypes.VARCHAR(1))
                        .expectArgumentTypes(DataTypes.VARCHAR(20)),
                TestSpec.forStrategy(
                                "OR with invalid implicit casting of null",
                                or(
                                        explicitSequence(DataTypes.STRING().notNull()),
                                        explicitSequence(DataTypes.INT().notNull()),
                                        explicitSequence(DataTypes.BIGINT().notNull())))
                        .calledWithArgumentTypes(DataTypes.NULL())
                        .expectErrorMessage(
                                "Invalid input arguments. Expected signatures are:\n"
                                        + "f(STRING NOT NULL)\nf(INT NOT NULL)\nf(BIGINT NOT NULL)"),
                TestSpec.forStrategy(
                                "OR with invalid type",
                                or(
                                        explicitSequence(DataTypes.INT()),
                                        explicitSequence(DataTypes.STRING())))
                        .calledWithArgumentTypes(DataTypes.BOOLEAN())
                        .expectErrorMessage(
                                "Invalid input arguments. Expected signatures are:\nf(INT)\nf(STRING)"),

                // invalid typed sequence
                TestSpec.forStrategy(explicitSequence(DataTypes.INT(), DataTypes.BOOLEAN()))
                        .calledWithArgumentTypes(DataTypes.BOOLEAN(), DataTypes.INT())
                        .expectErrorMessage(
                                "Invalid input arguments. Expected signatures are:\nf(INT, BOOLEAN)"),

                // sequence with wildcard
                TestSpec.forStrategy(sequence(ANY, explicit(DataTypes.INT())))
                        .calledWithArgumentTypes(DataTypes.BOOLEAN(), DataTypes.INT())
                        .calledWithArgumentTypes(DataTypes.BOOLEAN(), DataTypes.TINYINT())
                        .expectArgumentTypes(DataTypes.BOOLEAN(), DataTypes.INT()),

                // invalid named sequence
                TestSpec.forStrategy(
                                sequence(
                                        new String[] {"any", "int"},
                                        new ArgumentTypeStrategy[] {
                                            ANY, explicit(DataTypes.INT())
                                        }))
                        .calledWithArgumentTypes(DataTypes.STRING(), DataTypes.BOOLEAN())
                        .expectErrorMessage(
                                "Invalid input arguments. Expected signatures are:\nf(any <ANY>, int INT)"),

                // sequence with OR and implicit casting
                TestSpec.forStrategy(
                                sequence(
                                        explicit(DataTypes.INT()),
                                        or(
                                                explicit(DataTypes.BOOLEAN()),
                                                explicit(DataTypes.INT()))))
                        .expectSignature("f(INT, [BOOLEAN | INT])")
                        .calledWithArgumentTypes(DataTypes.INT(), DataTypes.INT())
                        .calledWithArgumentTypes(DataTypes.TINYINT(), DataTypes.TINYINT())
                        .expectArgumentTypes(DataTypes.INT(), DataTypes.INT()),

                // sequence with OR
                TestSpec.forStrategy(
                                sequence(
                                        explicit(DataTypes.INT()),
                                        or(
                                                explicit(DataTypes.BOOLEAN()),
                                                explicit(DataTypes.STRING()))))
                        .calledWithArgumentTypes(DataTypes.INT(), DataTypes.BIGINT())
                        .expectErrorMessage(
                                "Invalid input arguments. Expected signatures are:\nf(INT, [BOOLEAN | STRING])"),

                // sequence with literal
                TestSpec.forStrategy(sequence(LITERAL))
                        .calledWithLiteralAt(0)
                        .calledWithArgumentTypes(DataTypes.INT())
                        .expectArgumentTypes(DataTypes.INT()),

                // sequence with literal
                TestSpec.forStrategy(
                                sequence(
                                        and(LITERAL, explicit(DataTypes.STRING())),
                                        explicit(DataTypes.INT())))
                        .calledWithLiteralAt(0)
                        .calledWithArgumentTypes(DataTypes.STRING(), DataTypes.INT())
                        .expectSignature("f([<LITERAL NOT NULL> & STRING], INT)")
                        .expectArgumentTypes(DataTypes.STRING(), DataTypes.INT()),

                // sequence with missing literal
                TestSpec.forStrategy(
                                sequence(
                                        and(explicit(DataTypes.STRING()), LITERAL_OR_NULL),
                                        explicit(DataTypes.INT())))
                        .calledWithArgumentTypes(DataTypes.STRING(), DataTypes.INT())
                        .expectErrorMessage(
                                "Invalid input arguments. Expected signatures are:\nf([STRING & <LITERAL>], INT)"),

                // vararg sequence
                TestSpec.forStrategy(
                                varyingSequence(
                                        new String[] {"i", "s", "var"},
                                        new ArgumentTypeStrategy[] {
                                            explicit(DataTypes.INT()),
                                            explicit(DataTypes.STRING()),
                                            explicit(DataTypes.BOOLEAN())
                                        }))
                        .calledWithArgumentTypes(
                                DataTypes.INT(),
                                DataTypes.STRING(),
                                DataTypes.BOOLEAN(),
                                DataTypes.BOOLEAN(),
                                DataTypes.BOOLEAN())
                        .expectArgumentTypes(
                                DataTypes.INT(),
                                DataTypes.STRING(),
                                DataTypes.BOOLEAN(),
                                DataTypes.BOOLEAN(),
                                DataTypes.BOOLEAN()),

                // vararg sequence with conversion class
                TestSpec.forStrategy(
                                varyingSequence(
                                        new String[] {"var"},
                                        new ArgumentTypeStrategy[] {
                                            explicit(DataTypes.BOOLEAN().bridgedTo(boolean.class))
                                        }))
                        .calledWithArgumentTypes(
                                DataTypes.BOOLEAN(), DataTypes.BOOLEAN(), DataTypes.BOOLEAN())
                        .expectSignature("f(var BOOLEAN...)")
                        .expectArgumentTypes(
                                DataTypes.BOOLEAN().bridgedTo(boolean.class),
                                DataTypes.BOOLEAN().bridgedTo(boolean.class),
                                DataTypes.BOOLEAN().bridgedTo(boolean.class)),

                // vararg sequence
                TestSpec.forStrategy(
                                varyingSequence(
                                        new String[] {"i", "s", "var"},
                                        new ArgumentTypeStrategy[] {
                                            explicit(DataTypes.INT()),
                                            explicit(DataTypes.STRING()),
                                            explicit(DataTypes.BOOLEAN())
                                        }))
                        .calledWithArgumentTypes(DataTypes.INT(), DataTypes.STRING())
                        .expectArgumentTypes(DataTypes.INT(), DataTypes.STRING()),

                // invalid vararg type
                TestSpec.forStrategy(
                                varyingSequence(
                                        new String[] {"i", "s", "var"},
                                        new ArgumentTypeStrategy[] {
                                            explicit(DataTypes.INT()),
                                            explicit(DataTypes.STRING()),
                                            explicit(DataTypes.BOOLEAN())
                                        }))
                        .calledWithArgumentTypes(
                                DataTypes.INT(), DataTypes.STRING(), DataTypes.STRING())
                        .expectErrorMessage(
                                "Invalid input arguments. Expected signatures are:\nf(i INT, s STRING, var BOOLEAN...)"),

                // invalid non-vararg type
                TestSpec.forStrategy(
                                varyingSequence(
                                        new String[] {"i", "s", "var"},
                                        new ArgumentTypeStrategy[] {
                                            explicit(DataTypes.INT()),
                                            explicit(DataTypes.STRING()),
                                            explicit(DataTypes.BOOLEAN())
                                        }))
                        .calledWithArgumentTypes(
                                DataTypes.INT(), DataTypes.INT(), DataTypes.BOOLEAN())
                        .expectErrorMessage(
                                "Unsupported argument type. Expected type 'STRING' but actual type was 'INT'."),

                // OR in vararg type
                TestSpec.forStrategy(
                                varyingSequence(
                                        new String[] {"i", "s", "var"},
                                        new ArgumentTypeStrategy[] {
                                            explicit(DataTypes.INT()),
                                            explicit(DataTypes.STRING()),
                                            or(
                                                    explicit(DataTypes.BOOLEAN()),
                                                    explicit(DataTypes.INT()))
                                        }))
                        .calledWithArgumentTypes(
                                DataTypes.INT(),
                                DataTypes.STRING(),
                                DataTypes.INT(),
                                DataTypes.BOOLEAN())
                        .expectArgumentTypes(
                                DataTypes.INT(),
                                DataTypes.STRING(),
                                DataTypes.INT(),
                                DataTypes.BOOLEAN()),

                // invalid OR in vararg type
                TestSpec.forStrategy(
                                varyingSequence(
                                        new String[] {"i", "s", "var"},
                                        new ArgumentTypeStrategy[] {
                                            explicit(DataTypes.INT()),
                                            explicit(DataTypes.STRING()),
                                            or(
                                                    explicit(DataTypes.BOOLEAN()),
                                                    explicit(DataTypes.INT()))
                                        }))
                        .calledWithArgumentTypes(
                                DataTypes.INT(),
                                DataTypes.STRING(),
                                DataTypes.STRING(),
                                DataTypes.STRING())
                        .expectErrorMessage(
                                "Invalid input arguments. Expected signatures are:\nf(i INT, s STRING, var [BOOLEAN | INT]...)"),

                // incomplete inference
                TestSpec.forStrategy(WILDCARD)
                        .calledWithArgumentTypes(
                                DataTypes.NULL(), DataTypes.STRING(), DataTypes.NULL())
                        .expectSignature("f(*)")
                        .expectArgumentTypes(
                                DataTypes.NULL(), DataTypes.STRING(), DataTypes.NULL()),

                // typed arguments help inferring a type
                TestSpec.forStrategy(WILDCARD)
                        .typedArguments(
                                DataTypes.INT().bridgedTo(int.class),
                                DataTypes.STRING(),
                                DataTypes.BOOLEAN())
                        .calledWithArgumentTypes(
                                DataTypes.NULL(), DataTypes.STRING(), DataTypes.NULL())
                        .expectArgumentTypes(
                                DataTypes.INT().bridgedTo(int.class),
                                DataTypes.STRING(),
                                DataTypes.BOOLEAN()),

                // surrounding function helps inferring a type
                TestSpec.forStrategy(sequence(OUTPUT_IF_NULL, OUTPUT_IF_NULL, OUTPUT_IF_NULL))
                        .surroundingStrategy(explicitSequence(DataTypes.BOOLEAN()))
                        .calledWithArgumentTypes(
                                DataTypes.NULL(), DataTypes.STRING(), DataTypes.NULL())
                        .expectSignature("f(<OUTPUT>, <OUTPUT>, <OUTPUT>)")
                        .expectArgumentTypes(
                                DataTypes.BOOLEAN(), DataTypes.STRING(), DataTypes.BOOLEAN()),

                // surrounding function helps inferring a type
                TestSpec.forStrategy(sequence(or(OUTPUT_IF_NULL, explicit(DataTypes.INT()))))
                        .surroundingStrategy(explicitSequence(DataTypes.BOOLEAN()))
                        .calledWithArgumentTypes(DataTypes.NULL())
                        .expectSignature("f([<OUTPUT> | INT])")
                        .expectArgumentTypes(DataTypes.BOOLEAN()),

                // surrounding info can not infer input type and does not help inferring a type
                TestSpec.forStrategy(explicitSequence(DataTypes.BOOLEAN()))
                        .surroundingStrategy(WILDCARD)
                        .calledWithArgumentTypes(DataTypes.NULL())
                        .expectSignature("f(BOOLEAN)")
                        .expectArgumentTypes(DataTypes.BOOLEAN()),

                // surrounding function does not help inferring a type
                TestSpec.forStrategy(sequence(or(OUTPUT_IF_NULL, explicit(DataTypes.INT()))))
                        .calledWithArgumentTypes(DataTypes.NULL())
                        .expectSignature("f([<OUTPUT> | INT])")
                        .expectArgumentTypes(DataTypes.INT()),

                // typed arguments only with casting
                TestSpec.forStrategy(WILDCARD)
                        .typedArguments(DataTypes.INT(), DataTypes.STRING())
                        .calledWithArgumentTypes(DataTypes.TINYINT(), DataTypes.STRING())
                        .expectSignature("f(arg0 => INT, arg1 => STRING)")
                        .expectArgumentTypes(DataTypes.INT(), DataTypes.STRING()),

                // invalid typed arguments
                TestSpec.forStrategy(WILDCARD)
                        .typedArguments(DataTypes.INT(), DataTypes.STRING())
                        .calledWithArgumentTypes(DataTypes.STRING(), DataTypes.STRING())
                        .expectErrorMessage(
                                "Invalid argument type at position 0. Data type INT expected but STRING passed."),

                // named arguments
                TestSpec.forStrategy(WILDCARD)
                        .namedArguments("i", "s")
                        .typedArguments(DataTypes.INT(), DataTypes.STRING())
                        .expectSignature("f(i => INT, s => STRING)"),
                TestSpec.forStrategy(
                                "Wildcard with count verifies arguments number",
                                InputTypeStrategies.wildcardWithCount(
                                        ConstantArgumentCount.from(2)))
                        .calledWithArgumentTypes(DataTypes.STRING())
                        .expectErrorMessage(
                                "Invalid number of arguments. At least 2 arguments expected but 1 passed."),
                TestSpec.forStrategy(
                                "Array strategy infers a common type",
                                SpecificInputTypeStrategies.ARRAY)
                        .expectSignature("f(<COMMON>, <COMMON>...)")
                        .calledWithArgumentTypes(
                                DataTypes.INT().notNull(),
                                DataTypes.BIGINT().notNull(),
                                DataTypes.DOUBLE(),
                                DataTypes.DOUBLE().notNull())
                        .expectArgumentTypes(
                                DataTypes.DOUBLE(),
                                DataTypes.DOUBLE(),
                                DataTypes.DOUBLE(),
                                DataTypes.DOUBLE()),
                TestSpec.forStrategy(
                                "Array strategy fails for no arguments",
                                SpecificInputTypeStrategies.ARRAY)
                        .calledWithArgumentTypes()
                        .expectErrorMessage(
                                "Invalid number of arguments. At least 1 arguments expected but 0 passed."),
                TestSpec.forStrategy(
                                "Array strategy fails for null arguments",
                                SpecificInputTypeStrategies.ARRAY)
                        .calledWithArgumentTypes(DataTypes.NULL())
                        .expectErrorMessage("Could not find a common type for arguments: [NULL]"),
                TestSpec.forStrategy(
                                "Map strategy infers common types", SpecificInputTypeStrategies.MAP)
                        .calledWithArgumentTypes(
                                DataTypes.INT().notNull(),
                                DataTypes.DOUBLE(),
                                DataTypes.BIGINT().notNull(),
                                DataTypes.FLOAT().notNull())
                        .expectArgumentTypes(
                                DataTypes.BIGINT().notNull(),
                                DataTypes.DOUBLE(),
                                DataTypes.BIGINT().notNull(),
                                DataTypes.DOUBLE()),
                TestSpec.forStrategy(
                                "Map strategy fails for no arguments",
                                SpecificInputTypeStrategies.MAP)
                        .calledWithArgumentTypes()
                        .expectErrorMessage(
                                "Invalid number of arguments. At least 2 arguments expected but 0 passed."),
                TestSpec.forStrategy(
                                "Map strategy fails for an odd number of arguments",
                                SpecificInputTypeStrategies.MAP)
                        .calledWithArgumentTypes(
                                DataTypes.BIGINT(), DataTypes.BIGINT(), DataTypes.BIGINT())
                        .expectErrorMessage("Invalid number of arguments. 3 arguments passed."),
                TestSpec.forStrategy("Cast strategy", SpecificInputTypeStrategies.CAST)
                        .calledWithArgumentTypes(DataTypes.INT(), DataTypes.BIGINT())
                        .calledWithLiteralAt(1, DataTypes.BIGINT())
                        .expectSignature("f(<ANY>, <TYPE LITERAL>)")
                        .expectArgumentTypes(DataTypes.INT(), DataTypes.BIGINT()),
                TestSpec.forStrategy(
                                "Cast strategy for invalid target type",
                                SpecificInputTypeStrategies.CAST)
                        .calledWithArgumentTypes(DataTypes.BOOLEAN(), DataTypes.DATE())
                        .calledWithLiteralAt(1, DataTypes.DATE())
                        .expectErrorMessage("Unsupported cast from 'BOOLEAN' to 'DATE'."),
                TestSpec.forStrategy(
                                "Logical type roots instead of concrete data types",
                                sequence(
                                        logical(LogicalTypeRoot.VARCHAR),
                                        logical(LogicalTypeRoot.DECIMAL, true),
                                        logical(LogicalTypeRoot.DECIMAL),
                                        logical(LogicalTypeRoot.BOOLEAN),
                                        logical(LogicalTypeRoot.INTEGER, false),
                                        logical(LogicalTypeRoot.INTEGER)))
                        .calledWithArgumentTypes(
                                DataTypes.NULL(),
                                DataTypes.INT(),
                                DataTypes.DOUBLE(),
                                DataTypes.BOOLEAN().notNull(),
                                DataTypes.INT().notNull(),
                                DataTypes.INT().notNull())
                        .expectSignature(
                                "f(<VARCHAR>, <DECIMAL NULL>, <DECIMAL>, <BOOLEAN>, <INTEGER NOT NULL>, <INTEGER>)")
                        .expectArgumentTypes(
                                DataTypes.VARCHAR(1),
                                DataTypes.DECIMAL(10, 0),
                                DataTypes.DECIMAL(30, 15),
                                DataTypes.BOOLEAN().notNull(),
                                DataTypes.INT().notNull(),
                                DataTypes.INT().notNull()),
                TestSpec.forStrategy(
                                "Logical type roots with wrong implicit cast",
                                sequence(logical(LogicalTypeRoot.VARCHAR)))
                        .calledWithArgumentTypes(DataTypes.INT())
                        .expectSignature("f(<VARCHAR>)")
                        .expectErrorMessage(
                                "Unsupported argument type. Expected type root 'VARCHAR' but actual type was 'INT'."),
                TestSpec.forStrategy(
                                "Logical type roots with wrong nullability",
                                sequence(logical(LogicalTypeRoot.VARCHAR, false)))
                        .calledWithArgumentTypes(DataTypes.VARCHAR(5))
                        .expectSignature("f(<VARCHAR NOT NULL>)")
                        .expectErrorMessage(
                                "Unsupported argument type. Expected nullable type of root 'VARCHAR' but actual type was 'VARCHAR(5)'."),
                TestSpec.forStrategy(
                                "Logical type family instead of concrete data types",
                                sequence(
                                        logical(LogicalTypeFamily.CHARACTER_STRING, true),
                                        logical(LogicalTypeFamily.EXACT_NUMERIC),
                                        logical(LogicalTypeFamily.APPROXIMATE_NUMERIC),
                                        logical(LogicalTypeFamily.APPROXIMATE_NUMERIC),
                                        logical(LogicalTypeFamily.APPROXIMATE_NUMERIC, false)))
                        .calledWithArgumentTypes(
                                DataTypes.NULL(),
                                DataTypes.TINYINT(),
                                DataTypes.INT(),
                                DataTypes.BIGINT().notNull(),
                                DataTypes.DECIMAL(10, 2).notNull())
                        .expectSignature(
                                "f(<CHARACTER_STRING NULL>, <EXACT_NUMERIC>, <APPROXIMATE_NUMERIC>, <APPROXIMATE_NUMERIC>, <APPROXIMATE_NUMERIC NOT NULL>)")
                        .expectArgumentTypes(
                                DataTypes.VARCHAR(1),
                                DataTypes.TINYINT(),
                                DataTypes.DOUBLE(), // widening with preserved nullability
                                DataTypes.DOUBLE().notNull(), // widening with preserved nullability
                                DataTypes.DOUBLE().notNull()),
                TestSpec.forStrategy(
                                "Logical type family with invalid type",
                                sequence(logical(LogicalTypeFamily.EXACT_NUMERIC)))
                        .calledWithArgumentTypes(DataTypes.FLOAT())
                        .expectSignature("f(<EXACT_NUMERIC>)")
                        .expectErrorMessage(
                                "Unsupported argument type. Expected type of family 'EXACT_NUMERIC' but actual type was 'FLOAT'."),
                TestSpec.forStrategy(
                                "Constraint argument type strategy",
                                sequence(
                                        and(
                                                explicit(DataTypes.BOOLEAN()),
                                                constraint(
                                                        "%s must be nullable.",
                                                        args ->
                                                                args.get(0)
                                                                        .getLogicalType()
                                                                        .isNullable()))))
                        .calledWithArgumentTypes(DataTypes.BOOLEAN())
                        .expectSignature("f([BOOLEAN & <CONSTRAINT>])")
                        .expectArgumentTypes(DataTypes.BOOLEAN()),
                TestSpec.forStrategy(
                                "Constraint argument type strategy invalid",
                                sequence(
                                        and(
                                                explicit(DataTypes.BOOLEAN().notNull()),
                                                constraint(
                                                        "My constraint says %s must be nullable.",
                                                        args ->
                                                                args.get(0)
                                                                        .getLogicalType()
                                                                        .isNullable()))))
                        .calledWithArgumentTypes(DataTypes.BOOLEAN().notNull())
                        .expectErrorMessage(
                                "My constraint says BOOLEAN NOT NULL must be nullable."),
                TestSpec.forStrategy(
                                "Composite type strategy with ROW",
                                sequence(InputTypeStrategies.COMPOSITE))
                        .calledWithArgumentTypes(
                                DataTypes.ROW(DataTypes.FIELD("f0", DataTypes.BIGINT())))
                        .expectSignature("f(<COMPOSITE>)")
                        .expectArgumentTypes(
                                DataTypes.ROW(DataTypes.FIELD("f0", DataTypes.BIGINT()))),
                TestSpec.forStrategy(
                                "Composite type strategy with STRUCTURED type",
                                sequence(InputTypeStrategies.COMPOSITE))
                        .calledWithArgumentTypes(DataTypes.of(SimpleStructuredType.class).notNull())
                        .expectSignature("f(<COMPOSITE>)")
                        .expectArgumentTypes(DataTypes.of(SimpleStructuredType.class).notNull()),
                TestSpec.forStrategy(
                                "Same named arguments for overloaded method.",
                                or(
                                        sequence(explicit(DataTypes.STRING())),
                                        sequence(explicit(DataTypes.INT()))))
                        .namedArguments("sameName")
                        .calledWithArgumentTypes(DataTypes.BOOLEAN())
                        .expectErrorMessage(
                                "Invalid input arguments. Expected signatures are:\nf(STRING)\nf(INT)"),
                TestSpec.forStrategy(
                                "Common argument type strategy",
                                sequence(
                                        InputTypeStrategies.COMMON_ARG,
                                        InputTypeStrategies.COMMON_ARG))
                        .calledWithArgumentTypes(DataTypes.INT(), DataTypes.BIGINT())
                        .expectSignature("f(<COMMON>, <COMMON>)")
                        .expectArgumentTypes(DataTypes.BIGINT(), DataTypes.BIGINT()),
                TestSpec.forStrategy(
                                "ArrayElement argument type strategy",
                                sequence(
                                        logical(LogicalTypeRoot.ARRAY),
                                        SpecificInputTypeStrategies.ARRAY_ELEMENT_ARG))
                        .calledWithArgumentTypes(
                                DataTypes.ARRAY(DataTypes.INT().notNull()).notNull(),
                                DataTypes.INT())
                        .expectSignature("f(<ARRAY>, <ARRAY ELEMENT>)")
                        .expectArgumentTypes(
                                DataTypes.ARRAY(DataTypes.INT().notNull()).notNull(),
                                DataTypes.INT()),
                TestSpec.forStrategy(sequence(SpecificInputTypeStrategies.ARRAY_FULLY_COMPARABLE))
                        .expectSignature("f(<ARRAY<COMPARABLE>>)")
                        .calledWithArgumentTypes(DataTypes.ARRAY(DataTypes.ROW()))
                        .expectErrorMessage(
                                "Invalid input arguments. Expected signatures are:\n"
                                        + "f(<ARRAY<COMPARABLE>>)"),
                TestSpec.forStrategy(
                                "Strategy fails if input argument type is not ARRAY",
                                sequence(SpecificInputTypeStrategies.ARRAY_FULLY_COMPARABLE))
                        .calledWithArgumentTypes(DataTypes.INT())
                        .expectErrorMessage(
                                "Invalid input arguments. Expected signatures are:\n"
                                        + "f(<ARRAY<COMPARABLE>>)"),
                TestSpec.forStrategy(
                                "PROCTIME type strategy",
                                SpecificInputTypeStrategies.windowTimeIndicator(
                                        TimestampKind.PROCTIME))
                        .calledWithArgumentTypes(timeIndicatorType(TimestampKind.PROCTIME))
                        .expectSignature("f(<WINDOW REFERENCE>)")
                        .expectArgumentTypes(timeIndicatorType(TimestampKind.PROCTIME)),
                TestSpec.forStrategy(
                                "PROCTIME type strategy on non time indicator",
                                SpecificInputTypeStrategies.windowTimeIndicator(
                                        TimestampKind.PROCTIME))
                        .calledWithArgumentTypes(DataTypes.BIGINT())
                        .expectErrorMessage("Reference to a rowtime or proctime window required."),
                TestSpec.forStrategy(
                                "ROWTIME type strategy",
                                SpecificInputTypeStrategies.windowTimeIndicator(
                                        TimestampKind.ROWTIME))
                        .calledWithArgumentTypes(timeIndicatorType(TimestampKind.ROWTIME))
                        .expectSignature("f(<WINDOW REFERENCE>)")
                        .expectArgumentTypes(timeIndicatorType(TimestampKind.ROWTIME)),
                TestSpec.forStrategy(
                                "ROWTIME type strategy on proctime indicator",
                                SpecificInputTypeStrategies.windowTimeIndicator(
                                        TimestampKind.ROWTIME))
                        .calledWithArgumentTypes(timeIndicatorType(TimestampKind.PROCTIME))
                        .expectErrorMessage(
                                "A proctime window cannot provide a rowtime attribute."),
                TestSpec.forStrategy(
                                "PROCTIME type strategy on rowtime indicator",
                                SpecificInputTypeStrategies.windowTimeIndicator(
                                        TimestampKind.PROCTIME))
                        .calledWithArgumentTypes(timeIndicatorType(TimestampKind.ROWTIME))
                        .expectArgumentTypes(timeIndicatorType(TimestampKind.PROCTIME)),
                TestSpec.forStrategy(
                                "ROWTIME type strategy on long in batch mode",
                                SpecificInputTypeStrategies.windowTimeIndicator(
                                        TimestampKind.ROWTIME))
                        .calledWithArgumentTypes(DataTypes.BIGINT())
                        .expectArgumentTypes(DataTypes.BIGINT()),
                TestSpec.forStrategy(
                                "ROWTIME type strategy on non time attribute",
                                SpecificInputTypeStrategies.windowTimeIndicator(
                                        TimestampKind.ROWTIME))
                        .calledWithArgumentTypes(DataTypes.SMALLINT())
                        .expectErrorMessage("Reference to a rowtime or proctime window required."),
                TestSpec.forStrategy(
                                "PROCTIME type strategy on non time attribute",
                                SpecificInputTypeStrategies.windowTimeIndicator(
                                        TimestampKind.PROCTIME))
                        .calledWithArgumentTypes(DataTypes.SMALLINT())
                        .expectErrorMessage("Reference to a rowtime or proctime window required."),
                TestSpec.forStrategy(
                                "Reinterpret_cast strategy",
                                SpecificInputTypeStrategies.REINTERPRET_CAST)
                        .calledWithArgumentTypes(
                                DataTypes.DATE(), DataTypes.BIGINT(), DataTypes.BOOLEAN().notNull())
                        .calledWithLiteralAt(1, DataTypes.BIGINT())
                        .calledWithLiteralAt(2, true)
                        .expectSignature("f(<ANY>, <TYPE LITERAL>, <TRUE | FALSE>)")
                        .expectArgumentTypes(
                                DataTypes.DATE(),
                                DataTypes.BIGINT(),
                                DataTypes.BOOLEAN().notNull()),
                TestSpec.forStrategy(
                                "Reinterpret_cast strategy non literal overflow",
                                SpecificInputTypeStrategies.REINTERPRET_CAST)
                        .calledWithArgumentTypes(
                                DataTypes.DATE(), DataTypes.BIGINT(), DataTypes.BOOLEAN().notNull())
                        .calledWithLiteralAt(1, DataTypes.BIGINT())
                        .expectErrorMessage("Not null boolean literal expected for overflow."),
                TestSpec.forStrategy(
                                "Reinterpret_cast strategy not supported cast",
                                SpecificInputTypeStrategies.REINTERPRET_CAST)
                        .calledWithArgumentTypes(
                                DataTypes.INT(), DataTypes.BIGINT(), DataTypes.BOOLEAN().notNull())
                        .calledWithLiteralAt(1, DataTypes.BIGINT())
                        .calledWithLiteralAt(2, true)
                        .expectErrorMessage("Unsupported reinterpret cast from 'INT' to 'BIGINT'"),
                TestSpec.forStrategy("IndexArgumentTypeStrategy", sequence(INDEX))
                        .calledWithArgumentTypes(DataTypes.TINYINT())
                        .expectSignature("f(<INTEGER_NUMERIC>)")
                        .expectArgumentTypes(DataTypes.TINYINT()),
                TestSpec.forStrategy("IndexArgumentTypeStrategy", sequence(INDEX))
                        .calledWithArgumentTypes(DataTypes.INT())
                        .calledWithLiteralAt(0)
                        .expectArgumentTypes(DataTypes.INT()),
                TestSpec.forStrategy("IndexArgumentTypeStrategy BIGINT support", sequence(INDEX))
                        .calledWithArgumentTypes(DataTypes.BIGINT().notNull())
                        .calledWithLiteralAt(0, Long.MAX_VALUE)
                        .expectArgumentTypes(DataTypes.BIGINT().notNull()),
                TestSpec.forStrategy("IndexArgumentTypeStrategy index range", sequence(INDEX))
                        .calledWithArgumentTypes(DataTypes.INT().notNull())
                        .calledWithLiteralAt(0, -1)
                        .expectErrorMessage(
                                "Index must be an integer starting from '0', but was '-1'."),
                TestSpec.forStrategy("IndexArgumentTypeStrategy index type", sequence(INDEX))
                        .calledWithArgumentTypes(DataTypes.DECIMAL(10, 5))
                        .expectErrorMessage("Index can only be an INTEGER NUMERIC type."),

                // Percentage ArgumentStrategy
                TestSpec.forStrategy("normal", sequence(percentage(true)))
                        .calledWithArgumentTypes(DataTypes.DOUBLE())
                        .expectSignature("f(<NUMERIC>)")
                        .expectArgumentTypes(DataTypes.DOUBLE()),
                TestSpec.forStrategy("implicit cast", sequence(percentage(false)))
                        .calledWithArgumentTypes(DataTypes.DECIMAL(5, 2).notNull())
                        .expectSignature("f(<NUMERIC NOT NULL>)")
                        .expectArgumentTypes(DataTypes.DOUBLE().notNull()),
                TestSpec.forStrategy("literal", sequence(percentage(true)))
                        .calledWithArgumentTypes(DataTypes.DECIMAL(2, 2))
                        .calledWithLiteralAt(0, BigDecimal.valueOf(45, 2))
                        .expectArgumentTypes(DataTypes.DOUBLE()),
                TestSpec.forStrategy("literal", sequence(percentage(false)))
                        .calledWithArgumentTypes(DataTypes.INT().notNull())
                        .calledWithLiteralAt(0, 1)
                        .expectArgumentTypes(DataTypes.DOUBLE().notNull()),
                TestSpec.forStrategy("invalid type", sequence(percentage(true)))
                        .calledWithArgumentTypes(DataTypes.STRING())
                        .expectErrorMessage("Percentage must be of NUMERIC type."),
                TestSpec.forStrategy("invalid nullability", sequence(percentage(false)))
                        .calledWithArgumentTypes(DataTypes.DOUBLE())
                        .expectErrorMessage("Percentage must be of NOT NULL type."),
                TestSpec.forStrategy("invalid literal value", sequence(percentage(false)))
                        .calledWithArgumentTypes(DataTypes.DECIMAL(2, 1).notNull())
                        .calledWithLiteralAt(0, BigDecimal.valueOf(20, 1))
                        .expectErrorMessage(
                                "Percentage must be between [0.0, 1.0], but was '2.0'."),
                TestSpec.forStrategy("invalid literal value", sequence(percentage(false)))
                        .calledWithArgumentTypes(DataTypes.DECIMAL(2, 1).notNull())
                        .calledWithLiteralAt(0, BigDecimal.valueOf(-5, 1))
                        .expectErrorMessage(
                                "Percentage must be between [0.0, 1.0], but was '-0.5'."),

                // Percentage Array ArgumentStrategy
                TestSpec.forStrategy("normal", sequence(percentageArray(true)))
                        .calledWithArgumentTypes(DataTypes.ARRAY(DataTypes.DOUBLE()))
                        .expectSignature("f(ARRAY<NUMERIC>)")
                        .expectArgumentTypes(DataTypes.ARRAY(DataTypes.DOUBLE())),
                TestSpec.forStrategy("implicit cast", sequence(percentageArray(false)))
                        .calledWithArgumentTypes(
                                DataTypes.ARRAY(DataTypes.DECIMAL(5, 2).notNull()).notNull())
                        .expectSignature("f(ARRAY<NUMERIC NOT NULL> NOT NULL)")
                        .expectArgumentTypes(
                                DataTypes.ARRAY(DataTypes.DOUBLE().notNull()).notNull()),
                TestSpec.forStrategy("literal", sequence(percentageArray(true)))
                        .calledWithArgumentTypes(DataTypes.ARRAY(DataTypes.DOUBLE()))
                        .calledWithLiteralAt(0, new Double[] {0.45, 0.55})
                        .expectArgumentTypes(DataTypes.ARRAY(DataTypes.DOUBLE())),
                TestSpec.forStrategy("literal", sequence(percentageArray(false)))
                        .calledWithArgumentTypes(
                                DataTypes.ARRAY(DataTypes.DECIMAL(2, 2).notNull()).notNull())
                        .calledWithLiteralAt(
                                0,
                                new BigDecimal[] {
                                    BigDecimal.valueOf(45, 2), BigDecimal.valueOf(55, 2)
                                })
                        .expectArgumentTypes(
                                DataTypes.ARRAY(DataTypes.DOUBLE().notNull()).notNull()),
                TestSpec.forStrategy("literal", sequence(percentageArray(true)))
                        .calledWithArgumentTypes(DataTypes.ARRAY(DataTypes.INT()))
                        .calledWithLiteralAt(0, new Integer[] {0, 1})
                        .expectArgumentTypes(DataTypes.ARRAY(DataTypes.DOUBLE())),
                TestSpec.forStrategy("empty literal array", sequence(percentageArray(true)))
                        .calledWithArgumentTypes(DataTypes.ARRAY(DataTypes.DOUBLE()))
                        .calledWithLiteralAt(0, new Double[0])
                        .expectArgumentTypes(DataTypes.ARRAY(DataTypes.DOUBLE())),
                TestSpec.forStrategy("not array", sequence(percentageArray(true)))
                        .calledWithArgumentTypes(DataTypes.DOUBLE())
                        .expectErrorMessage("Percentage must be an array."),
                TestSpec.forStrategy("invalid array nullability", sequence(percentageArray(false)))
                        .calledWithArgumentTypes(DataTypes.ARRAY(DataTypes.STRING().notNull()))
                        .expectErrorMessage("Percentage must be a non-null array."),
                TestSpec.forStrategy("invalid element type", sequence(percentageArray(true)))
                        .calledWithArgumentTypes(DataTypes.ARRAY(DataTypes.STRING()))
                        .expectErrorMessage(
                                "Value in the percentage array must be of NUMERIC type."),
                TestSpec.forStrategy(
                                "invalid element nullability", sequence(percentageArray(false)))
                        .calledWithArgumentTypes(DataTypes.ARRAY(DataTypes.DOUBLE()).notNull())
                        .expectErrorMessage(
                                "Value in the percentage array must be of NOT NULL type."),
                TestSpec.forStrategy("invalid literal", sequence(percentageArray(true)))
                        .calledWithArgumentTypes(DataTypes.ARRAY(DataTypes.DOUBLE()))
                        .calledWithLiteralAt(0, new Double[] {0.5, 1.5})
                        .expectErrorMessage(
                                "Value in the percentage array must be between [0.0, 1.0], but was '1.5'."),
                TestSpec.forStrategy("invalid literal", sequence(percentageArray(true)))
                        .calledWithArgumentTypes(DataTypes.ARRAY(DataTypes.DECIMAL(3, 2)))
                        .calledWithLiteralAt(
                                0,
                                new BigDecimal[] {
                                    BigDecimal.valueOf(-1, 1), BigDecimal.valueOf(5, 1)
                                })
                        .expectErrorMessage(
                                "Value in the percentage array must be between [0.0, 1.0], but was '-0.1'."));
    }

    private static DataType timeIndicatorType(TimestampKind timestampKind) {
        return TypeConversions.fromLogicalToDataType(
                new LocalZonedTimestampType(false, timestampKind, 3));
    }

    /** Simple pojo that should be converted to a Structured type. */
    public static class SimpleStructuredType {
        public long f0;
    }
}
