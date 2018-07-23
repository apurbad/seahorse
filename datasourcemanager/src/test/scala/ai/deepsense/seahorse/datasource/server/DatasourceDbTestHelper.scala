/**
  * Copyright 2018 deepsense.ai (CodiLime, Inc)
  *
  * Licensed under the Apache License, Version 2.0 (the "License");
  * you may not use this file except in compliance with the License.
  * You may obtain a copy of the License at
  *
  * http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing, software
  * distributed under the License is distributed on an "AS IS" BASIS,
  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  * See the License for the specific language governing permissions and
  * limitations under the License.
  */
package ai.deepsense.seahorse.datasource.server

import java.util.UUID

import ai.deepsense.seahorse.datasource.db.Database
import ai.deepsense.seahorse.datasource.db.schema.DatasourcesSchema.{SparkOptionDB, sparkOptionsTable}

object DatasourceDbTestHelper {

  def getSparkOptionsByDatasourceId(datasourceId: UUID): Seq[SparkOptionDB] = {
    import Database.api._
    import scala.concurrent.ExecutionContext.Implicits.global
    import ai.deepsense.seahorse.datasource.api.DatasourceManagerApi.DBIOOps
    val dbio = for {
      sparkOptions <- sparkOptionsTable.filter(_.datasourceId === datasourceId).result
    } yield sparkOptions
    dbio.run()
  }
}