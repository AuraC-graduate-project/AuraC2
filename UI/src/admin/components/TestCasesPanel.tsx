import { useState, useEffect } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from './ui/card';
import { Button } from './ui/button';
import { Plus, Pencil, Eye, EyeOff } from 'lucide-react';
import { AddTestCaseModal } from './AddTestCaseModal';
import { EditTestCaseModal } from './EditTestCaseModal';
import { getTestCasesForProblem } from '../services/api';
import { TestCaseResponse } from '../types/api';
import { toast } from 'sonner';
import { StatusBadge } from '../../components/StatusBadge';

interface TestCasesPanelProps {
  problemId: number;
  problemTitle: string;
}

export function TestCasesPanel({ problemId, problemTitle }: TestCasesPanelProps) {
  const [testCases, setTestCases] = useState<TestCaseResponse[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [addModalOpen, setAddModalOpen] = useState(false);
  const [editModalOpen, setEditModalOpen] = useState(false);
  const [editingTestCase, setEditingTestCase] = useState<TestCaseResponse | null>(null);

  const loadTestCases = async () => {
    setIsLoading(true);
    try {
      const data = await getTestCasesForProblem(problemId);
      setTestCases(data);
    } catch (error) {
      console.error('Failed to load test cases:', error);
      toast.error('Failed to load test cases');
      setTestCases([]);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadTestCases();
  }, [problemId]);

  return (
    <>
      <Card className="border border-gray-200 shadow-sm mt-6">
        <CardHeader className="border-b border-slate-200 bg-slate-50">
          <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
            <div>
              <CardTitle className="text-lg text-slate-950">Test Cases</CardTitle>
              <p className="mt-1 text-sm text-slate-600">{problemTitle}</p>
            </div>
            <Button 
              size="sm"
              className="gap-2 bg-blue-700 hover:bg-blue-800"
              onClick={() => setAddModalOpen(true)}
            >
              <Plus className="w-4 h-4" />
              Add Test Case
            </Button>
          </div>
        </CardHeader>
        <CardContent className="p-6">
          {isLoading ? (
            <p className="text-slate-600 py-8 text-center">Loading test cases...</p>
          ) : testCases.length === 0 ? (
            <p className="text-slate-600 py-8 text-center">
              No test cases yet. Click "Add Test Case" to create a sample or hidden judge test.
            </p>
          ) : (
            <div className="grid gap-4 lg:grid-cols-2">
              {testCases.map((testCase, index) => (
                <div 
                  key={testCase.id} 
                  className="border border-slate-200 rounded-lg p-4 bg-white"
                >
                   <div className="flex items-center justify-between mb-3">
                     <div>
                       <h4 className="font-semibold text-slate-900">Test Case #{index + 1}</h4>
                       <div className="mt-1 flex items-center gap-2">
                         {testCase.isPublic ? (
                           <Eye className="h-4 w-4 text-cyan-700" />
                         ) : (
                           <EyeOff className="h-4 w-4 text-slate-500" />
                         )}
                         <StatusBadge
                           kind="testcase"
                           value={testCase.isPublic ? 'PUBLIC SAMPLE' : 'PRIVATE'}
                           label={testCase.isPublic ? 'Public sample' : 'Private hidden'}
                         />
                       </div>
                     </div>
                     <div className="flex items-center gap-2">
                       <Button
                         size="sm"
                         variant="outline"
                         className="h-7 px-2"
                         onClick={() => {
                           setEditingTestCase(testCase);
                           setEditModalOpen(true);
                         }}
                       >
                         <Pencil className="w-3.5 h-3.5" />
                       </Button>
                     </div>
                   </div>
                  
                  <div className="space-y-3">
                    <div>
                      <p className="text-xs font-semibold uppercase tracking-wide text-slate-500 mb-1">Input Data</p>
                      <pre className="bg-slate-950 text-slate-100 border border-slate-800 rounded p-3 text-xs overflow-x-auto">
                        {testCase.inputData}
                      </pre>
                    </div>
                    
                    <div>
                      <p className="text-xs font-semibold uppercase tracking-wide text-slate-500 mb-1">Expected Output</p>
                      <pre className="bg-slate-50 border border-slate-200 rounded p-3 text-xs overflow-x-auto text-slate-800">
                        {testCase.expectedOutput}
                      </pre>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>

      <AddTestCaseModal 
        open={addModalOpen}
        onOpenChange={setAddModalOpen}
        problemId={problemId}
        onSuccess={loadTestCases}
      />

      {editingTestCase && (
        <EditTestCaseModal
          open={editModalOpen}
          onOpenChange={setEditModalOpen}
          testCase={editingTestCase}
          onSuccess={loadTestCases}
        />
      )}
    </>
  );
}
