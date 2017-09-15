/**
  * Copyright (c) 2016, CodiLime Inc.
  */

package io.deepsense.e2etests

import java.io.File
import java.net.URL
import java.util.UUID

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps
import scalaz.Scalaz._
import scalaz._
import akka.actor.ActorSystem
import akka.util.Timeout
import org.scalactic.source.Position
import org.scalatest.Matchers
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Seconds, Span}
import spray.http.HttpResponse

import io.deepsense.api.datasourcemanager.model.DatasourceParams
import io.deepsense.commons.models.ClusterDetails
import io.deepsense.commons.utils.Logging
import io.deepsense.commons.utils.OptionOpts._
import io.deepsense.deeplang.CatalogRecorder
import io.deepsense.deeplang.catalogs.CatalogPair
import io.deepsense.graph.nodestate.name.NodeStatusName
import io.deepsense.models.json.graph.GraphJsonProtocol.GraphReader
import io.deepsense.models.json.workflow.WorkflowWithVariablesJsonProtocol
import io.deepsense.models.workflows.{Workflow, WorkflowInfo, WorkflowWithVariables}
import io.deepsense.sessionmanager.rest.client.SessionManagerClient
import io.deepsense.sessionmanager.service.Status
import io.deepsense.workflowmanager.client.WorkflowManagerClient

trait SeahorseIntegrationTestDSL
    extends Matchers
    with Eventually
    with Logging
    with WorkflowWithVariablesJsonProtocol {



  protected val dockerComposePath = "../deployment/docker-compose/"

  private val localJarsDir = new File(dockerComposePath, "jars")
  private val localJarPaths = getJarsFrom(localJarsDir)
  protected val jarsInDockerPaths = localJarPaths
    .map(f => new File("/resources/jars", f.getName))
    .map(_.toURI.toURL)

  private def catalogRecorder: CatalogRecorder =
    CatalogRecorder.fromJars(localJarPaths.map(_.toURI.toURL))
  private def catalogs = catalogRecorder.catalogs
  final def operablesCatalog = catalogs.dOperableCatalog
  final def operationsCatalog = catalogs.dOperationsCatalog
  override def graphReader = new GraphReader(operationsCatalog)

  import scala.concurrent.ExecutionContext.Implicits.global

  protected val httpTimeout = 10 seconds
  protected val workflowTimeout = 30 minutes

  protected val baseUrl = new URL("http", "localhost", 33321, "")
  protected val workflowsUrl = new URL(baseUrl, "/v1/workflows/")
  protected val sessionsUrl = new URL(baseUrl, "/v1/sessions/")
  protected val datasourcesUrl = new URL(baseUrl, "/datasourcemanager/v1/")

  private val userId = UUID.fromString("dd63e120-548f-4ac9-8fd5-092bad7616ab")
  private val userName = "Seahorse test"

  implicit val as: ActorSystem = ActorSystem()
  implicit val timeout: Timeout = httpTimeout

  implicit val patience = PatienceConfig(
    timeout = Span(60, Seconds),
    interval = Span(10, Seconds)
  )

  val wmclient = new WorkflowManagerClient(
    workflowsUrl,
    userId,
    userName,
    None
  )

  val smclient = new SessionManagerClient(
    sessionsUrl,
    userId,
    userName,
    None
  )

  val dsclient = new DatasourcesClient(
    datasourcesUrl,
    userId,
    userName
  )

  def ensureSeahorseIsRunning(): Unit = {
    eventually {
      logger.info("Waiting for Seahorse to boot up...")
      Await.result(smclient.fetchSessions(), httpTimeout)
      Await.result(wmclient.fetchWorkflows(), httpTimeout)
      // If wm is running, DatasourceManager is also running.
    }
  }

  protected def uploadWorkflow(fileContents: String): Future[WorkflowInfo] = {
    for {
      id <- wmclient.uploadWorkflow(fileContents)
      workflows <- wmclient.fetchWorkflows()
      workflow <- workflows.find(_.id == id).asFuture
    } yield {
      workflow
    }
  }

  protected def runAndCleanupWorkflow(workflow: WorkflowInfo, cluster: ClusterDetails): Future[Unit] = {
    for {
      _ <- launchWorkflow(cluster, workflow)
      validation = assertAllNodesCompletedSuccessfully(workflow)
      _ <- cleanSession(workflow)
    } yield {
      validation match {
        case Success(_) =>
        case Failure(nodeReport) =>
          fail(s"Some nodes failed for workflow id: ${workflow.id}." +
            s"name: ${workflow.name}'. Node report: $nodeReport. Cluster failed: ${cluster.name}. " +
            s"Details in log containing ${cluster.name} string.")
      }
    }
  }

  private def cleanSession(workflow: WorkflowInfo): Future[Unit] = {
    val id = workflow.id
    for {
      _ <- smclient.deleteSession(id)
      _ <- wmclient.deleteWorkflow(id)
    } yield ()
  }

  private def launchWorkflow(cluster: ClusterDetails, workflow: WorkflowInfo): Future[HttpResponse] = {
    val id = workflow.id
    createSessionSynchronously(id, cluster)
    smclient.launchSession(id)
  }

  protected def createSessionSynchronously(id: Workflow.Id, clusterDetails: ClusterDetails): Unit = {
    smclient.createSession(id, clusterDetails)

    eventually {
      Await.result(
        smclient.fetchSession(id).map { s =>
          s.status shouldBe Status.Running
        }, httpTimeout)
    }
  }

  protected def assertAllNodesCompletedSuccessfully(workflow: WorkflowInfo): Validation[String, String] = {
    val numberOfNodesFut = calculateNumberOfNodes(workflow.id)

    val nodesResult: Validation[String, String] = eventually {

      val nodesStatuses = smclient.queryNodeStatuses(workflow.id)
      Await.result(
        for {
          nsr <- nodesStatuses
          errorNodeStatuses = nsr.nodeStatuses.getOrElse(Map.empty).getOrElse(NodeStatusName.Failed, 0)
          completedNodes = nsr.nodeStatuses.getOrElse(Map.empty).getOrElse(NodeStatusName.Completed, 0)
          numberOfNodes <- numberOfNodesFut
        } yield {
          checkCompletedNodesNumber(
            errorNodeStatuses,
            completedNodes,
            numberOfNodes,
            workflow.id,
            workflow.name
          )
        }, httpTimeout)
    }(PatienceConfig(timeout = workflowTimeout, interval = 5 seconds), implicitly[Position])

    nodesResult
  }

  protected def checkCompletedNodesNumber(
      errorNodeStatuses: Int,
      completedNodes: Int,
      numberOfNodes: Int,
      workflowID: Workflow.Id,
      workflowName: String): Validation[String, String] = {
    if (errorNodeStatuses > 0) {
      s"Errors: $errorNodeStatuses nodes failed for workflow id: $workflowID. name: $workflowName".failure
    } else {
      logger.info(s"$completedNodes nodes completed." +
        s" Need all $numberOfNodes nodes completed for workflow id: $workflowID. name: $workflowName")

      if (completedNodes > numberOfNodes) {
        logger.error(
          s"""FATAL. INVESTIGATE
              |
              |Number of completed nodes is larger than number of nodes
            """.
            stripMargin)
      }

      completedNodes shouldEqual numberOfNodes

      "All nodes completed".success
    }
  }

  private def calculateNumberOfNodes(workflowId: Workflow.Id): Future[Int] = {
    wmclient.fetchWorkflow(workflowId).map(_.graph.nodes.size)
  }

  protected def insertDatasource(uuid: UUID, datasourceParams: DatasourceParams): Unit = {
    dsclient.insertDatasource(uuid, datasourceParams)
  }

  private def getJarsFrom(dir: File): Seq[File] = {
    dir.listFiles.filter(f => f.isFile && f.getName.endsWith(".jar"))
  }
}
