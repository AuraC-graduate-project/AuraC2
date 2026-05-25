import { useState, useEffect, useMemo } from 'react';
import { Button } from './ui/button';
import { Input } from './ui/input';
import { Plus, Pencil, Eye, EyeOff, Trash2 } from 'lucide-react';
import { AddTestCaseModal } from './AddTestCaseModal';
import { EditTestCaseModal } from './EditTestCaseModal';
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from './ui/alert-dialog';
import { deleteTestCase, getTestCasesForProblem } from '../services/api';
import { TestCaseResponse } from '../types/api';
import { toast } from 'sonner';
import { StatusBadge } from '../../components/StatusBadge';
import { AdminHelpTooltip } from './AdminHelpTooltip';

interface TestCasesPanelProps {
  problemId: number;
  problemTitle: string;
  embedded?: boolean;
}

type TestCaseVisibilityFilter = 'all' | 'public' | 'hidden';

export function TestCasesPanel({ problemId, problemTitle, embedded = false }: TestCasesPanelProps) {
  const [testCases, setTestCases] = useState<TestCaseResponse[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [addModalOpen, setAddModalOpen] = useState(false);
  const [editModalOpen, setEditModalOpen] = useState(false);
  const [editingTestCase, setEditingTestCase] = useState<TestCaseResponse | null>(null);
  const [testCaseToDelete, setTestCaseToDelete] = useState<TestCaseResponse | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [visibilityFilter, setVisibilityFilter] = useState<TestCaseVisibilityFilter>('all');
  const [query, setQuery] = useState('');

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

  const handleDeleteTestCase = async () => {
    if (!testCaseToDelete) return;

    setIsDeleting(true);
    try {
      await deleteTestCase(testCaseToDelete.id);
      toast.success('Test case deleted successfully');
      setTestCaseToDelete(null);
      await loadTestCases();
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to delete test case');
    } finally {
      setIsDeleting(false);
    }
  };

  const filteredTestCases = useMemo(() => {
    const normalizedQuery = query.trim().toLowerCase();
    return testCases.filter((testCase, index) => {
      if (visibilityFilter === 'public' && !testCase.isPublic) return false;
      if (visibilityFilter === 'hidden' && testCase.isPublic) return false;
      if (!normalizedQuery) return true;
      return (
        String(index + 1).includes(normalizedQuery) ||
        testCase.inputData.toLowerCase().includes(normalizedQuery) ||
        testCase.expectedOutput.toLowerCase().includes(normalizedQuery)
      );
    });
  }, [query, testCases, visibilityFilter]);

  const publicCount = testCases.filter((testCase) => testCase.isPublic).length;
  const hiddenCount = testCases.length - publicCount;

  return (
    <>
      <section className={`${embedded ? '' : 'mt-6'} rounded-lg border border-gray-200 bg-white shadow-sm`}>
        <div className="border-b border-slate-200 bg-slate-50 p-4">
          <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
            <div>
              <div className="flex items-center gap-2">
                <h3 className="text-lg font-semibold text-slate-950">Test Cases</h3>
                <AdminHelpTooltip
                  label="Test cases help"
                  content="Public samples appear in team statements and safe exports. Private hidden tests are used for judging only."
                />
              </div>
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
        </div>
        <div className="p-4">
          {isLoading ? (
            <p className="text-slate-600 py-8 text-center">Loading test cases...</p>
          ) : testCases.length === 0 ? (
            <p className="text-slate-600 py-8 text-center">
              No test cases yet. Click "Add Test Case" to create a sample or hidden judge test.
            </p>
          ) : (
            <div className="space-y-4">
              <div className="flex flex-col gap-3 xl:flex-row xl:items-center xl:justify-between">
                <div className="flex flex-wrap gap-2">
                  {[
                    { value: 'all' as const, label: `All (${testCases.length})` },
                    { value: 'public' as const, label: `Public samples (${publicCount})` },
                    { value: 'hidden' as const, label: `Hidden (${hiddenCount})` },
                  ].map((option) => (
                    <button
                      key={option.value}
                      type="button"
                      className={`rounded-md border px-3 py-1.5 text-sm font-semibold transition ${
                        visibilityFilter === option.value
                          ? 'border-blue-700 bg-blue-50 text-blue-700'
                          : 'border-slate-200 bg-white text-slate-600 hover:border-slate-300'
                      }`}
                      onClick={() => setVisibilityFilter(option.value)}
                    >
                      {option.label}
                    </button>
                  ))}
                </div>
                <Input
                  value={query}
                  onChange={(event) => setQuery(event.target.value)}
                  className="h-9 xl:max-w-xs"
                  placeholder="Search input or output..."
                />
              </div>

              {filteredTestCases.length === 0 ? (
                <p className="rounded-md border border-dashed border-slate-300 bg-slate-50 p-6 text-center text-sm text-slate-500">
                  No test cases match the current filters.
                </p>
              ) : (
              <div className="grid max-h-[720px] gap-3 overflow-y-auto pr-1 lg:grid-cols-2">
              {filteredTestCases.map((testCase) => (
                <div
                  key={testCase.id} 
                  className="border border-slate-200 rounded-lg p-4 bg-white"
                >
                   <div className="flex items-center justify-between mb-3">
                     <div>
                       <h4 className="font-semibold text-slate-900">Test Case #{testCases.findIndex((item) => item.id === testCase.id) + 1}</h4>
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
                       <Button
                         size="sm"
                         variant="outline"
                         className="h-7 px-2 border-rose-200 text-rose-700 hover:bg-rose-50 hover:text-rose-800"
                         onClick={() => setTestCaseToDelete(testCase)}
                       >
                         <Trash2 className="w-3.5 h-3.5" />
                       </Button>
                     </div>
                   </div>
                  
                  <div className="space-y-3">
                    <div>
                      <p className="text-xs font-semibold uppercase tracking-wide text-slate-500 mb-1">Input Data</p>
                      <pre className="max-h-44 overflow-auto rounded border border-slate-800 bg-slate-950 p-3 text-xs text-slate-100">
                        {testCase.inputData}
                      </pre>
                    </div>
                    
                    <div>
                      <p className="text-xs font-semibold uppercase tracking-wide text-slate-500 mb-1">Expected Output</p>
                      <pre className="max-h-44 overflow-auto rounded border border-slate-200 bg-slate-50 p-3 text-xs text-slate-800">
                        {testCase.expectedOutput}
                      </pre>
                    </div>
                  </div>
                </div>
              ))}
              </div>
              )}
            </div>
          )}
        </div>
      </section>

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

      <AlertDialog
        open={Boolean(testCaseToDelete)}
        onOpenChange={(open) => {
          if (!open && !isDeleting) setTestCaseToDelete(null);
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete test case?</AlertDialogTitle>
            <AlertDialogDescription>
              This permanently removes the selected test case from "{problemTitle}". This cannot be undone.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={isDeleting}>Cancel</AlertDialogCancel>
            <AlertDialogAction
              className="bg-rose-600 text-white hover:bg-rose-700"
              disabled={isDeleting}
              onClick={(event) => {
                event.preventDefault();
                handleDeleteTestCase();
              }}
            >
              {isDeleting ? 'Deleting...' : 'Delete Test Case'}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}
