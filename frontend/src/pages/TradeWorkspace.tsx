import { Link } from "react-router-dom"
import { ArrowRight, CheckCircle2, MessageSquareText, Sparkles } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import { tradePipeline, tradeScenarios } from "@/features/trade-workspace/tradeScenarios"

export default function TradeWorkspace() {
  return (
    <div className="flex flex-col gap-4 animate-fade-in">
      <section className="trade-workspace-hero">
        <div className="min-w-0">
          <div className="page-kicker">EXPORT AGENT WORKSPACE</div>
          <h1 className="mt-3 text-[30px] font-black leading-none tracking-tight text-[#171916] md:text-[38px]">
            外贸场景作战台
          </h1>
          <p className="mt-3 max-w-[760px] text-sm font-medium leading-relaxed text-[#74766F]">
            把 AI Agent 放进询盘、报价、打样、物流、售后这些真实外贸环节里，不只是聊天，而是帮业务员拆解任务、补齐信息、生成可交付内容。
          </p>
          <div className="mt-5 flex flex-wrap gap-2">
            <Link to="/agent-chat">
              <Button className="active:translate-y-[1px]">
                <MessageSquareText size={16} />
                开始外贸对话
              </Button>
            </Link>
            <Link to="/knowledge-base">
              <Button variant="outline" className="active:translate-y-[1px]">
                维护产品知识库
                <ArrowRight size={15} />
              </Button>
            </Link>
          </div>
        </div>
        <div className="trade-workspace-brief">
          <div className="flex items-center justify-between gap-3">
            <span className="text-[11px] font-black uppercase tracking-[0.08em] text-[#74766F]">Agent Focus</span>
            <Sparkles size={16} className="text-[#2F6B5F]" />
          </div>
          <div className="mt-5 grid gap-3">
            {["先问清需求，再生成回复", "产品推荐必须带链接或说明缺口", "报价前检查 MOQ、包装、交期", "复杂任务拆成可跟踪步骤"].map((item) => (
              <div key={item} className="flex items-start gap-3 text-sm font-bold text-[#171916]">
                <CheckCircle2 size={16} className="mt-0.5 shrink-0 text-[#2F6B5F]" />
                <span>{item}</span>
              </div>
            ))}
          </div>
        </div>
      </section>

      <section className="grid grid-cols-1 gap-3 md:grid-cols-5">
        {tradePipeline.map((stage, index) => (
          <div key={stage.value} className="trade-pipeline-step" style={{ animationDelay: `${index * 70}ms` }}>
            <span>{stage.label}</span>
            <strong>{stage.value}</strong>
            <small>{stage.hint}</small>
          </div>
        ))}
      </section>

      <section className="grid grid-cols-1 gap-3 xl:grid-cols-[minmax(0,1fr)_340px]">
        <div className="grid grid-cols-1 gap-3 lg:grid-cols-2">
          {tradeScenarios.map((scenario, index) => (
            <Card key={scenario.id} className="trade-scenario-card animate-fade-in-up" style={{ animationDelay: `${index * 55}ms` }}>
              <CardContent className="p-4">
                <div className="flex items-start justify-between gap-3">
                  <div className="flex min-w-0 items-start gap-3">
                    <div className="trade-scenario-icon">
                      <scenario.icon size={18} />
                    </div>
                    <div className="min-w-0">
                      <h2 className="text-[15px] font-black leading-tight text-[#171916]">{scenario.title}</h2>
                      <p className="mt-1 text-xs leading-relaxed text-[#74766F]">{scenario.subtitle}</p>
                    </div>
                  </div>
                  <span className="trade-owner-chip">{scenario.owner}</span>
                </div>

                <div className="mt-4 grid grid-cols-1 gap-2 sm:grid-cols-2">
                  <InfoLine label="节点" value={scenario.stage} />
                  <InfoLine label="价值" value={scenario.impact} />
                </div>

                <div className="mt-4 flex flex-wrap gap-2">
                  {scenario.actions.map((action) => (
                    <span key={action} className="trade-action-chip">{action}</span>
                  ))}
                </div>

                <Link
                  to={`/agent-chat?prompt=${encodeURIComponent(scenario.prompt)}`}
                  className="mt-4 inline-flex items-center gap-2 text-xs font-black text-[#1F5F53] hover:text-[#171916]"
                >
                  带着这个场景去对话
                  <ArrowRight size={14} />
                </Link>
              </CardContent>
            </Card>
          ))}
        </div>

        <aside className="trade-playbook">
          <div className="text-[11px] font-black uppercase tracking-[0.08em] text-[#74766F]">Playbook</div>
          <h2 className="mt-2 text-[20px] font-black leading-tight text-[#171916]">建议重构方向</h2>
          <div className="mt-4 space-y-3">
            <PlaybookItem title="知识库产品化" text="把产品规格、材质、MOQ、包装、认证、产品链接都作为结构化字段维护，回答时强制引用。" />
            <PlaybookItem title="询盘流转标准化" text="每条询盘先打标签，再决定进入报价、追问、放弃或长期培育。" />
            <PlaybookItem title="Agent 可执行化" text="让 Agent 输出下一步动作、负责人、所需资料和客户话术，而不是只生成一段文字。" />
            <PlaybookItem title="微信与邮件分工" text="紧急提醒走 JC claw，正式报价和打样确认走邮件模板，避免渠道混乱。" />
          </div>
        </aside>
      </section>
    </div>
  )
}

function InfoLine({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-[8px] border border-[#E4E8E5] bg-[#F8FBFA] px-3 py-2">
      <div className="text-[10px] font-black text-[#74766F]">{label}</div>
      <div className="mt-1 text-[12px] font-bold leading-snug text-[#171916]">{value}</div>
    </div>
  )
}

function PlaybookItem({ title, text }: { title: string; text: string }) {
  return (
    <div className="border-t border-[#E4E8E5] pt-3">
      <h3 className="text-sm font-black text-[#171916]">{title}</h3>
      <p className="mt-1 text-xs leading-relaxed text-[#74766F]">{text}</p>
    </div>
  )
}
