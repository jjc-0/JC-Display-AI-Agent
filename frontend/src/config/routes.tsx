import { lazy, type ReactNode } from "react"

const AgentChat = lazy(() => import("@/pages/AgentChat"))
const AgentExecutionCenter = lazy(() => import("@/pages/AgentExecutionCenter"))
const AgentSquare = lazy(() => import("@/pages/AgentSquare"))
const ApiIntegration = lazy(() => import("@/pages/ApiIntegration"))
const Analysis = lazy(() => import("@/pages/Analysis"))
const AuthCenter = lazy(() => import("@/pages/AuthCenter"))
const Channels = lazy(() => import("@/pages/Channels"))
const CopyWriting = lazy(() => import("@/pages/CopyWriting"))
const Dashboard = lazy(() => import("@/pages/Dashboard"))
const ImageRecognition = lazy(() => import("@/pages/ImageRecognition"))
const InquiryScoring = lazy(() => import("@/pages/InquiryScoring"))
const JCClaw = lazy(() => import("@/pages/JCClaw"))
const KnowledgeBase = lazy(() => import("@/pages/KnowledgeBase"))
const ProductImage = lazy(() => import("@/pages/ProductImage"))
const Profile = lazy(() => import("@/pages/Profile"))
const Templates = lazy(() => import("@/pages/Templates"))
const TradeWorkspace = lazy(() => import("@/pages/TradeWorkspace"))
const Translate = lazy(() => import("@/pages/Translate"))
const WorkflowBuilder = lazy(() => import("@/pages/WorkflowBuilder"))

export interface AppRoute {
  path: string
  element: ReactNode
}

export const protectedRoutes: AppRoute[] = [
  { path: "dashboard", element: <Dashboard /> },
  { path: "agent-chat", element: <AgentChat /> },
  { path: "trade-workspace", element: <TradeWorkspace /> },
  { path: "agent-square", element: <AgentSquare /> },
  { path: "agent-execution", element: <AgentExecutionCenter /> },
  { path: "inquiry", element: <InquiryScoring /> },
  { path: "copywriting", element: <CopyWriting /> },
  { path: "translate", element: <Translate /> },
  { path: "analysis", element: <Analysis /> },
  { path: "image-recognition", element: <ImageRecognition /> },
  { path: "knowledge-base", element: <KnowledgeBase /> },
  { path: "templates", element: <Templates /> },
  { path: "channels", element: <Channels /> },
  { path: "api-integration", element: <ApiIntegration /> },
  { path: "auth-center", element: <AuthCenter /> },
  { path: "workflow", element: <WorkflowBuilder /> },
  { path: "jc-claw", element: <JCClaw /> },
  { path: "product-image", element: <ProductImage /> },
  { path: "profile", element: <Profile /> },
]
