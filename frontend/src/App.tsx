import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom"
import Layout from "@/components/layout/Layout"
import Dashboard from "@/pages/Dashboard"
import AgentChat from "@/pages/AgentChat"
import AgentSquare from "@/pages/AgentSquare"
import AgentExecutionCenter from "@/pages/AgentExecutionCenter"
import InquiryScoring from "@/pages/InquiryScoring"
import CopyWriting from "@/pages/CopyWriting"
import Translate from "@/pages/Translate"
import Analysis from "@/pages/Analysis"
import ImageRecognition from "@/pages/ImageRecognition"
import KnowledgeBase from "@/pages/KnowledgeBase"
import Templates from "@/pages/Templates"
import Channels from "@/pages/Channels"
import ApiIntegration from "@/pages/ApiIntegration"
import AuthCenter from "@/pages/AuthCenter"
import WorkflowBuilder from "@/pages/WorkflowBuilder"
import JCClaw from "@/pages/JCClaw"
import ProductImage from "@/pages/ProductImage"

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Layout />}>
          <Route index element={<Navigate to="/dashboard" replace />} />
          <Route path="dashboard" element={<Dashboard />} />
          <Route path="agent-chat" element={<AgentChat />} />
          <Route path="agent-square" element={<AgentSquare />} />
          <Route path="agent-execution" element={<AgentExecutionCenter />} />
          <Route path="inquiry" element={<InquiryScoring />} />
          <Route path="copywriting" element={<CopyWriting />} />
          <Route path="translate" element={<Translate />} />
          <Route path="analysis" element={<Analysis />} />
          <Route path="image-recognition" element={<ImageRecognition />} />
          <Route path="knowledge-base" element={<KnowledgeBase />} />
          <Route path="templates" element={<Templates />} />
          <Route path="channels" element={<Channels />} />
          <Route path="api-integration" element={<ApiIntegration />} />
          <Route path="auth-center" element={<AuthCenter />} />
          <Route path="workflow" element={<WorkflowBuilder />} />
          <Route path="jc-claw" element={<JCClaw />} />
          <Route path="product-image" element={<ProductImage />} />
          <Route path="*" element={<Navigate to="/dashboard" replace />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}
