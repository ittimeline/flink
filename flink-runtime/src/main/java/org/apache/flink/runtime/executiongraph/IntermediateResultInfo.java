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

package org.apache.flink.runtime.executiongraph;

import org.apache.flink.runtime.jobgraph.IntermediateDataSetID;

public interface IntermediateResultInfo {
    /**
     * Get the intermediate result id.
     *
     * @return the intermediate result id
     */
    IntermediateDataSetID getResultId();

    /**
     * Checks whether there is a single subpartition that contains all the produced data.
     *
     * @return true if one subpartition that contains all the data; false otherwise.
     */
    boolean isSingleSubpartitionContainsAllData();

    /**
     * Determines whether the associated intermediate data set uses a broadcast distribution
     * pattern.
     *
     * <p>A broadcast distribution pattern indicates that all data produced by this intermediate
     * data set should be broadcast to every downstream consumer.
     *
     * @return true if the intermediate data set is using a broadcast distribution pattern; false
     *     otherwise.
     */
    boolean isBroadcast();

    /**
     * Whether it is a pointwise result.
     *
     * @return whether it is a pointwise result
     */
    boolean isPointwise();

    /**
     * Get number of partitions for this result.
     *
     * @return the number of partitions in this result
     */
    int getNumPartitions();

    /**
     * Get number of subpartitions for the given partition.
     *
     * @param partitionIndex the partition index
     * @return the number of subpartitions of the partition
     */
    int getNumSubpartitions(int partitionIndex);
}
